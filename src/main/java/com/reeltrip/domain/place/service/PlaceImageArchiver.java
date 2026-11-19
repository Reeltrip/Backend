package com.reeltrip.domain.place.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.reeltrip.domain.image.ImageValidator;
import com.reeltrip.infra.fetcher.web.RemoteImageClient;
import com.reeltrip.infra.storage.StorageClient;

/**
 * 카드 사진을 우리 저장소로 옮겨요 (#87).
 * 링크 분석 카드는 원본 썸네일 주소를 쓰는데, 인스타그램 주소에는 만료 시각이 있어서 며칠 뒤 사진이 사라져요.
 * Google Places 사진은 여기로 오지 않아요(검색 사진은 저장하지 않는다는 결정 그대로).
 */
@Service
public class PlaceImageArchiver {
    private static final Logger log = LoggerFactory.getLogger(PlaceImageArchiver.class);
    private static final String STORAGE_PREFIX = "https://storage.googleapis.com/";
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(5);

    private final RemoteImageClient remote;
    private final ImageValidator validator;
    private final StorageClient storage;
    private final boolean enabled;

    public PlaceImageArchiver(RemoteImageClient remote, ImageValidator validator, StorageClient storage,
                              @Value("${app.images.archive-external:true}") boolean enabled) {
        this.remote = remote;
        this.validator = validator;
        this.storage = storage;
        this.enabled = enabled;
    }

    /** 옮겨야 하는 주소인지. 우리 저장소 주소와 만료가 없는 유튜브 썸네일은 그대로 둬요. */
    public boolean needsArchive(String imageUrl) {
        if (!enabled || imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith(STORAGE_PREFIX)) {
            return false;
        }
        try {
            String host = URI.create(imageUrl).getHost();
            return host != null && !host.toLowerCase(Locale.ROOT).endsWith(".ytimg.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 저장소로 옮긴 주소를 돌려줘요. 옮기지 못하면 원래 주소를 그대로 돌려줘서 카드 저장을 막지 않아요. */
    public String archive(String imageUrl) {
        if (!needsArchive(imageUrl)) {
            return imageUrl;
        }
        // 어느 단계에서 막혔는지 로그로 구분해요 (내려받기 / 형식 검사 / 저장)
        String stage = "download";
        try {
            var image = remote.download(imageUrl, DOWNLOAD_TIMEOUT);
            stage = "validate";
            String extension = validator.validate(image.bytes(), image.contentType());
            stage = "store";
            return storage.store("places/archive/" + UUID.randomUUID() + "." + extension, image.bytes(), image.contentType());
        } catch (IOException | RuntimeException e) {
            // 주소에는 서명 값이 들어 있어서 호스트만 남겨요.
            log.warn("카드 사진을 저장소로 옮기지 못했어요. stage={} host={}", stage, hostOf(imageUrl));
            return imageUrl;
        }
    }

    private static String hostOf(String imageUrl) {
        try { return URI.create(imageUrl).getHost(); } catch (IllegalArgumentException e) { return "unknown"; }
    }
}
