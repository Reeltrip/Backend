package com.reeltrip.domain.source.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.source.dto.SourceCreateRequest;
import com.reeltrip.domain.source.dto.SourceCreateResponse;
import com.reeltrip.domain.source.dto.SourceDetailResponse;
import com.reeltrip.domain.source.dto.SourceLinkResponse;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.domain.source.entity.SourceLink;
import com.reeltrip.domain.source.event.SourceAnalysisRequested;
import com.reeltrip.domain.source.repository.SourceLinkRepository;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.PlaceDraft;

@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository sources;
    private final SourceLinkRepository links;
    private final UserRepository users;
    private final SourceUrlParser urlParser;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final AnalysisStateService analysis;

    public SourceService(SourceRepository sources, SourceLinkRepository links, UserRepository users,
                         SourceUrlParser urlParser, ObjectMapper mapper, ApplicationEventPublisher events, AnalysisStateService analysis) {
        this.sources = sources;
        this.links = links;
        this.users = users;
        this.urlParser = urlParser;
        this.mapper = mapper;
        this.events = events;
        this.analysis = analysis;
    }

    @Transactional
    public SourceCreateResponse createSource(Long userId, SourceCreateRequest request) {
        var parsed = request.urls().stream().map(urlParser::parse).toList();
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Source source = sources.save(Source.create(user));
        links.saveAll(parsed.stream().map(url -> SourceLink.create(source, url.url(), url.platform())).toList());
        // 분석 리스너는 커밋된 source_link를 읽도록 AFTER_COMMIT에서 연결해요(#23).
        events.publishEvent(new SourceAnalysisRequested(source.getSourceId()));
        return new SourceCreateResponse(source.getSourceId(), AnalysisStatus.PENDING);
    }

    @Transactional
    public SourceDetailResponse getSource(Long userId, Long sourceId) {
        analysis.recover(sourceId,userId);
        Source source = sources.findBySourceIdAndUser_UserId(sourceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_NOT_FOUND));
        var responses = links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(sourceId).stream()
                .map(this::toLinkResponse).toList();
        return new SourceDetailResponse(source.getSourceId(), source.getTitle(), source.getStatus(), responses);
    }

    private SourceLinkResponse toLinkResponse(SourceLink link) {
        if (link.getStatus() == AnalysisStatus.FAILED) {
            return new SourceLinkResponse(link.getSourceLinkId(), link.getUrl(), link.getStatus(), List.of(),
                    link.getFailCode(), link.getFailMessage());
        }
        List<PlaceDraft> drafts = link.getStatus() == AnalysisStatus.DONE ? readDrafts(link) : List.of();
        return new SourceLinkResponse(link.getSourceLinkId(), link.getUrl(), link.getStatus(), drafts, null, null);
    }

    private List<PlaceDraft> readDrafts(SourceLink link) {
        try {
            List<PlaceDraft> drafts = mapper.readValue(link.getDraftJson(), new TypeReference<List<PlaceDraft>>() { });
            if (drafts == null || drafts.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException();
            }
            return drafts.stream().map(draft -> new PlaceDraft(
                    link.getSourceLinkId(), draft.category(), draft.name(), draft.address(), draft.latitude(),
                    draft.longitude(), draft.googlePlaceId(), draft.city(), draft.imageUrl(), link.getUrl(),
                    draft.sourceTimestampSec(), draft.keyPoints(), draft.priceText(), draft.reviewCount(),
                    draft.openingHours(), draft.accessText(), draft.memo(), draft.subType(), draft.applicableRegion(),
                    draft.cost(), draft.benefit(), draft.method(), draft.placeText(), draft.caution())).toList();
        } catch (JsonProcessingException | IllegalArgumentException error) {
            log.error("카드 초안을 읽지 못했어요. sourceLinkId={}", link.getSourceLinkId());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
