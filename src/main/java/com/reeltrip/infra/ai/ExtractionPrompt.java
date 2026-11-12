package com.reeltrip.infra.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.infra.fetcher.FetchedContent;

/** 카드 추출 프롬프트. 시스템 지시문은 prompts/card-extraction.txt에 카테고리 목록을 끼워 만들어요. */
public class ExtractionPrompt {

    private static final String TEMPLATE_PATH = "prompts/card-extraction.txt";
    private static final String CATEGORIES_PLACEHOLDER = "{{CATEGORIES}}";
    // 블로그 본문이 아주 길 때 토큰 비용이 끝없이 늘지 않게 잘라요. 시험한 네이버 블로그는 약 1만 1천 자였어요.
    static final int MAX_TEXT_LENGTH = 30_000;

    private final String systemInstruction;

    private ExtractionPrompt(String systemInstruction) {
        this.systemInstruction = systemInstruction;
    }

    public static ExtractionPrompt load() {
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ExtractionPrompt(template.replace(CATEGORIES_PLACEHOLDER, describeCategories()));
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽지 못했어요: " + TEMPLATE_PATH, e);
        }
    }

    public String systemInstruction() {
        return systemInstruction;
    }

    /** 영상과 같이 보내는 요청 문장 */
    public String videoRequest() {
        return "이 영상에 나온 여행 정보를 카드로 정리해 줘.";
    }

    /** 가져온 본문과 같이 보내는 요청 문장 */
    public String textRequest(FetchedContent content) {
        String text = content.text().strip();
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        return "아래 " + describePlatform(content.platform()) + "에 나온 여행 정보를 카드로 정리해 줘.\n"
                + "원본 주소: " + content.url() + "\n"
                + "---\n"
                + text;
    }

    private static String describeCategories() {
        return Arrays.stream(Category.values())
                .map(category -> category.isPlace()
                        ? "- " + category.name() + " (" + category.getDisplayName() + "): 장소 카드, 핵심 포인트 = "
                                + category.getKeyPointName()
                        : "- " + category.name() + " (" + category.getDisplayName() + "): 교통·팁 카드")
                .collect(Collectors.joining("\n"));
    }

    private static String describePlatform(Platform platform) {
        if (platform == null) {
            return "글";
        }
        return switch (platform) {
            case INSTAGRAM -> "인스타그램 게시물 캡션";
            case NAVER_BLOG -> "블로그 글";
            default -> "글";
        };
    }
}
