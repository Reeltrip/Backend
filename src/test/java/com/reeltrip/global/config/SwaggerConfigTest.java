package com.reeltrip.global.config;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Swagger UI·OpenAPI 문서 (FE 연동 확인용, 2026-11-14) */
class SwaggerConfigTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class Enabled {

        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("로그인 없이 OpenAPI 문서를 받을 수 있고 JWT Bearer 인증 방식이 들어 있다.")
        void apiDocs_withoutLogin_ok() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("Wayvi API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.paths['/places'].post").exists())
                    .andExpect(jsonPath("$.paths['/plans/{planId}'].get").exists());
        }

        @Test
        @DisplayName("로그인 회원 id(@LoginUser)는 토큰에서 꺼내는 값이라 문서의 요청 파라미터에 나오지 않는다.")
        void apiDocs_loginUserHidden() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/places'].post.parameters").doesNotExist())
                    .andExpect(jsonPath("$.paths['/places'].get.parameters[*].name", hasItem("category")))
                    .andExpect(jsonPath("$.paths['/places'].get.parameters[*].name", not(hasItem("userId"))));
        }

        @Test
        @DisplayName("계획 목록 조회 조건은 객체 하나가 아니라 owner·status·period 같은 쿼리 파라미터로 보인다.")
        void apiDocs_planListQueryFlattened() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/plans'].get.parameters[*].name", hasItem("owner")))
                    .andExpect(jsonPath("$.paths['/plans'].get.parameters[*].name", hasItem("period")))
                    .andExpect(jsonPath("$.paths['/plans'].get.parameters[*].name", not(hasItem("request"))));
        }

        @Test
        @DisplayName("카드 수정 본문은 형식 없는 JSON이 아니라 카드 요청 형식으로 보인다.")
        void apiDocs_placePatchBodySchema() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/places/{placeId}'].patch.requestBody.content['application/json'].schema['$ref']")
                            .value("#/components/schemas/PlaceCardRequest"));
        }

        @Test
        @DisplayName("API 묶음 이름은 한국어로 보이고 인증부터 공유까지 화면 흐름 순서로 나온다.")
        void apiDocs_koreanTagsInFlowOrder() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.tags[0].name").value("인증"))
                    .andExpect(jsonPath("$.tags[*].name", hasItem("AI 초안")))
                    .andExpect(jsonPath("$.tags[*].name", not(hasItem("plan-draft-controller"))))
                    .andExpect(jsonPath("$.paths['/plans/{planId}/ai-drafts'].post.tags[0]").value("AI 초안"));
        }

        @Test
        @DisplayName("API마다 한국어 요약과 설명이 붙고, 주소·쿼리 값에도 한국어 설명이 보인다.")
        void apiDocs_koreanSummaries() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/plans/{planId}/ai-drafts'].post.summary").value("AI 초안 만들기"))
                    .andExpect(jsonPath("$.paths['/plans/{planId}/ai-drafts'].post.description")
                            .value(org.hamcrest.Matchers.containsString("숙소")))
                    .andExpect(jsonPath("$.paths['/places'].post.summary").value("카드 저장 (여러 장)"))
                    .andExpect(jsonPath("$.paths['/plans'].get.parameters[?(@.name == 'owner')].description",
                            hasItem(org.hamcrest.Matchers.containsString("shared"))));
        }

        @Test
        @DisplayName("로그인 없이 Swagger UI 화면을 열 수 있다.")
        void swaggerUi_withoutLogin_ok() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = "SWAGGER_ENABLED=false")
    @AutoConfigureMockMvc
    class Disabled {

        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("SWAGGER_ENABLED=false면 OpenAPI 문서를 내려주지 않는다.")
        void apiDocs_disabled_notFound() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isNotFound());
        }
    }
}
