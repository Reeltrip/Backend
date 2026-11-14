package com.reeltrip.domain.source.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.source.dto.SourceListResponse;
import com.reeltrip.domain.source.dto.SourceSummaryResponse;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

@Service
public class SourceHistoryService {

    private final SourceRepository sources;
    private final UserRepository users;

    public SourceHistoryService(SourceRepository sources, UserRepository users) {
        this.sources = sources;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public SourceListResponse getSources(Long userId, int page, int size) {
        // JPA의 시작 위치는 int 범위예요. 큰 page * size도 오버플로 없이 거부해요.
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!users.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("sourceId")));
        var summaries = sources.findAllByUser_UserId(userId, pageable).stream()
                .map(SourceSummaryResponse::from).toList();
        return new SourceListResponse(summaries);
    }
}
