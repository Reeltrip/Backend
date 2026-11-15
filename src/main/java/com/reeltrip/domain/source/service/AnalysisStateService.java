package com.reeltrip.domain.source.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.domain.source.entity.SourceLink;
import com.reeltrip.domain.source.repository.SourceLinkRepository;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.PlaceDraft;

@Service
public class AnalysisStateService {
    private final SourceRepository sources;
    private final SourceLinkRepository links;
    private final ObjectMapper mapper;
    private final Clock clock;
    public AnalysisStateService(SourceRepository sources,SourceLinkRepository links,ObjectMapper mapper,Clock clock) {
        this.sources=sources; this.links=links; this.mapper=mapper; this.clock=clock;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true)
    public List<Long> pending(Long sourceId) {
        return links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(sourceId).stream()
                .filter(link->link.getStatus()==AnalysisStatus.PENDING).map(SourceLink::getSourceLinkId).toList();
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Work claim(Long linkId) {
        var state=load(linkId);
        if(state==null || state.link().getStatus()!=AnalysisStatus.PENDING || terminal(state.source().getStatus())) { return null; }
        if(state.source().getStatus()==AnalysisStatus.PENDING) { state.source().markAnalyzing(); }
        state.link().markAnalyzing();
        return new Work(linkId,state.link().getUrl());
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void complete(Long linkId,List<PlaceDraft> drafts) {
        var state=load(linkId);
        if(state==null || state.link().getStatus()!=AnalysisStatus.ANALYZING || terminal(state.source().getStatus())) { return; }
        try {
            String json=mapper.writeValueAsString(drafts);
            if(drafts.isEmpty() || drafts.size()>50 || json.getBytes(StandardCharsets.UTF_8).length>60*1024) {
                state.link().markFailed(ErrorCode.AI_EXTRACTION_FAILED);
            } else { state.link().markDone(json); }
        } catch(Exception e) { state.link().markFailed(ErrorCode.AI_EXTRACTION_FAILED); }
        aggregate(state.source(),state.links());
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean fail(Long linkId,ErrorCode code) {
        var state=load(linkId);
        if(state==null || terminal(state.link().getStatus()) || terminal(state.source().getStatus())) { return false; }
        state.link().markFailed(code);
        aggregate(state.source(),state.links());
        return true;
    }

    @Transactional
    public void recover(Long sourceId,Long userId) {
        var source=sources.findOwnedForUpdate(sourceId,userId).orElseThrow(()->new BusinessException(ErrorCode.SOURCE_NOT_FOUND));
        if(terminal(source.getStatus())) { return; }
        var children=links.findAllForUpdate(sourceId);
        var cutoff=LocalDateTime.now(clock).minusMinutes(3);
        for(var link:children) {
            if(!terminal(link.getStatus()) && link.getUpdatedAt()!=null && !link.getUpdatedAt().isAfter(cutoff)) {
                link.markFailed(ErrorCode.SOURCE_ANALYSIS_TIMEOUT);
            }
        }
        aggregate(source,children);
    }

    private Locked load(Long linkId) {
        Long parent=links.parentId(linkId).orElse(null);
        if(parent==null) { return null; }
        var source=sources.findForUpdate(parent).orElse(null);
        if(source==null) { return null; }
        // 부모→모든 링크 순서로 현재 값을 잠가 동시 완료 집계가 유실되지 않게 해요.
        var children=links.findAllForUpdate(parent);
        var link=children.stream().filter(child->child.getSourceLinkId().equals(linkId)).findFirst().orElse(null);
        return link==null?null:new Locked(source,link,children);
    }

    private void aggregate(Source source,List<SourceLink> children) {
        if(terminal(source.getStatus()) || children.isEmpty() || children.stream().anyMatch(link->!terminal(link.getStatus()))) { return; }
        String title=source.getCreatedAt().toLocalDate()+" 수집";
        var cities=new LinkedHashMap<String,Integer>();
        for(var link:children) {
            if(link.getStatus()==AnalysisStatus.DONE) {
                try {
                    for(var card:mapper.readTree(link.getDraftJson())) {
                        String city=card.path("city").asText("").strip();
                        if(!city.isEmpty()) { cities.merge(city,1,Integer::sum); }
                    }
                } catch(Exception ignored) { }
            }
        }
        String city=null; int highest=0;
        for(var entry:cities.entrySet()) { if(entry.getValue()>highest) { city=entry.getKey(); highest=entry.getValue(); } }
        if(city!=null) {
            int end=Math.min(city.length(),47);
            if(Character.isHighSurrogate(city.charAt(end-1))) { end--; }
            title=city.substring(0,end)+" 여행";
        }
        if(children.stream().anyMatch(link->link.getStatus()==AnalysisStatus.DONE)) {
            if(source.getStatus()==AnalysisStatus.PENDING) { source.markAnalyzing(); }
            source.markDone(title);
        } else { source.markFailed(title); }
    }

    private static boolean terminal(AnalysisStatus status) { return status==AnalysisStatus.DONE || status==AnalysisStatus.FAILED; }
    private record Locked(Source source,SourceLink link,List<SourceLink> links) { }
    public record Work(Long linkId,String url) { }
}
