package com.reeltrip.domain.place.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.reeltrip.domain.place.repository.PlaceRepository;

/**
 * 이미 저장된 카드의 만료되는 사진을 저장소로 옮겨요 (#87).
 * 서버가 뜰 때 한 번 돌아요. 옮긴 카드는 다음부터 대상이 아니라서 여러 번 돌아도 결과가 같아요.
 */
@Component
public class PlaceImageBackfill {
    private static final Logger log = LoggerFactory.getLogger(PlaceImageBackfill.class);
    /** 한 번에 옮기는 사진 수. 서버가 뜰 때 외부 호출이 끝없이 이어지지 않게 해요. */
    private static final int MAX_IMAGES = 300;

    private final PlaceRepository placeRepository;
    private final PlaceImageArchiver archiver;
    private final boolean runsOnStartup;

    public PlaceImageBackfill(PlaceRepository placeRepository, PlaceImageArchiver archiver,
                              @Value("${app.images.backfill-on-startup:true}") boolean runsOnStartup) {
        this.placeRepository = placeRepository;
        this.archiver = archiver;
        this.runsOnStartup = runsOnStartup;
    }

    /** 요청 처리를 막지 않게 따로 돌려요. */
    @EventListener(ApplicationReadyEvent.class)
    public void startAfterBoot() {
        if (!runsOnStartup) {
            return;
        }
        Thread.ofVirtual().name("place-image-backfill").start(() -> {
            try {
                log.info("카드 사진 옮기기를 마쳤어요. moved={}", run());
            } catch (RuntimeException e) {
                log.warn("카드 사진 옮기기를 끝내지 못했어요.");
            }
        });
    }

    /** 옮긴 사진 수(주소 기준)를 돌려줘요. 같은 사진을 쓰는 카드는 한 번에 바꿔요. */
    public int run() {
        int moved = 0;
        var targets = placeRepository.findDistinctImageUrls().stream().filter(archiver::needsArchive).limit(MAX_IMAGES).toList();
        for (String imageUrl : targets) {
            String archived = archiver.archive(imageUrl);
            if (archived != null && !archived.equals(imageUrl)) {
                placeRepository.replaceImageUrl(imageUrl, archived);
                moved++;
            }
        }
        return moved;
    }
}
