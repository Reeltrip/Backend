// BE 2가 추가 (2026-11-14). Swagger로 FE가 API를 보고 바로 호출해 볼 수 있게 해요.
package com.reeltrip.global.config;

import static java.util.Map.entry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.reeltrip.global.security.LoginUser;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Swagger UI 우측 위 "Authorize"에 액세스 토큰(Bearer 뒤 값)만 넣으면 모든 요청에 붙어요.
 * 로그인이 필요 없는 /auth/** 등도 자물쇠가 보이지만 토큰 없이 불러도 돼요.
 *
 * <p>한국어 묶음 이름·요약·설명은 컨트롤러마다 어노테이션을 붙이지 않고 여기 한곳에 모았어요.
 * BE 1·BE 2 컨트롤러를 건드리지 않고 고칠 수 있게요. 새 API를 만들면 OPERATIONS에 한 줄 추가해 주세요
 * (없으면 Swagger에 요약 없이 주소만 보여요).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    /** springdoc이 컨트롤러 이름으로 만든 묶음 → 한국어 묶음. 넣은 순서가 Swagger 화면 순서예요(화면 흐름 순). */
    private static final Map<String, Tag> TAGS = new LinkedHashMap<>();

    static {
        // @LoginUser 회원 id는 토큰에서 꺼내는 값이라 요청 파라미터로 보이지 않게 해요.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);

        tag("auth-controller", "인증", "로그인과 토큰 재발급 (S0-1a). 로그인 없이 불러요.");
        tag("local-dev-login-controller", "개발용 로그인",
                "로컬 전용이에요(local 프로필 + DEV_LOGIN_ENABLED=true). 배포 서버에는 없어요.");
        tag("user-controller", "회원", "내 정보 (S0-2)");
        tag("source-controller", "링크 분석", "링크를 보내 분석을 시작하고, 링크별 상태와 카드 초안을 받아요 (S1).");
        tag("source-history-controller", "수집 내역", "내 최근 링크 수집 내역을 조회해요 (S1 링크 홈).");
        tag("place-controller", "요약카드", "보관함 카드 저장·조회·수정·삭제 (S2, S3)");
        tag("image-controller", "사진", "카드에 넣을 사진 올리기 (S2-2, S3-3)");
        tag("place-search-controller", "장소 검색", "이름으로 현지 주소·좌표 찾기 (S3-3)");
        tag("plan-controller", "여행계획", "계획 만들기·목록·보기·수정·삭제·복제 (S4, S5)");
        tag("plan-edit-controller", "계획 편집", "숙소·구역·일자별 동선 저장, 일자 순서 바꾸기 (S4-3 ~ S4-7)");
        tag("plan-draft-controller", "AI 초안", "AI가 숙소와 일자별 동선을 채워요 (S4-2).");
        tag("route-controller", "경로", "두 장소 사이 가장 빠른 경로 (S4-7a, S4-7b)");
        tag("plan-share-controller", "공유", "읽기 전용 / 함께 편집 링크 (S4-8)");
    }

    /** "메서드 주소" → [요약, 설명] */
    private static final Map<String, String[]> OPERATIONS = Map.ofEntries(
            op("POST /auth/email-codes", "가입 인증번호 받기",
                    "6자리 번호를 메일로 보내요. 5분 동안 유효하고 60초 뒤부터 다시 받을 수 있어요. 이미 가입된 이메일은 DUPLICATE_EMAIL, 60초 안 재요청은 EMAIL_CODE_TOO_FREQUENT예요. (S0-1b)"),
            op("POST /auth/email-codes/verify", "가입 인증번호 확인",
                    "받은 번호를 확인해요. 틀리면 INVALID_EMAIL_CODE, 5분이 지나면 EMAIL_CODE_EXPIRED, 5번 틀리면 EMAIL_CODE_ATTEMPTS_EXCEEDED예요. 확인 후 30분 안에 가입해요. (S0-1b)"),
            op("POST /auth/signup", "회원가입",
                    "인증을 마친 이메일로 가입하고 바로 로그인 토큰을 받아요. 비밀번호는 영문·숫자 포함 8자 이상, 닉네임 1~8자, 필수 약관 2개 동의가 필요해요. (S0-1b)"),
            op("POST /auth/login", "로그인",
                    "이메일·비밀번호로 액세스 토큰과 리프레시 토큰을 받아요. 틀리면 INVALID_LOGIN이에요. (S0-1a)"),
            op("POST /auth/refresh", "토큰 재발급",
                    "리프레시 토큰으로 새 액세스·리프레시 토큰을 받아요. 한 번 쓴 리프레시 토큰은 다시 쓸 수 없어요."),
            op("POST /auth/dev-login", "개발용 로그인 (로컬 전용)",
                    "이메일·닉네임으로 회원을 찾거나 새로 만들고 액세스 토큰을 줘요. "
                            + "응답의 accessToken을 오른쪽 위 Authorize에 넣으면 다른 API를 바로 불러 볼 수 있어요."),
            op("GET /users/me", "내 정보", "닉네임, 보관함 카드 수, 여행 수를 줘요. (S0-2)"),
            op("POST /sources", "링크 분석 시작",
                    "링크 여러 개를 한 번에 보내면 바로 202와 sourceId를 줘요. 분석 결과는 GET /sources/{sourceId}로 확인해요. (S1-2)"),
            op("GET /sources/{sourceId}", "링크 분석 상태 조회",
                    "링크별 상태(PENDING → ANALYZING → DONE 또는 FAILED)와 카드 초안, 실패 이유를 줘요. "
                            + "FE는 몇 초마다 불러 확인해요. (S1-2, S1-3)"),
            op("GET /sources", "최근 수집 내역", "내가 보낸 링크 분석 요청을 최신순으로 줘요. (링크 홈)"),
            op("POST /images", "사진 올리기",
                    "사진 파일(file)을 올리면 검사 뒤 Cloud Storage에 저장하고 공개 주소를 줘요. 그 주소를 카드의 imageUrl에 넣어요. (S2-2, S3-3)"),
            op("GET /place-search", "장소 검색",
                    "장소·도시·구역 이름으로 Google Places를 검색해 현지 주소·좌표를 줘요. 직접 추가 카드와 구역 선택에 써요. (S3-3, S4-4)"),
            op("GET /routes", "구간 경로",
                    "두 카드(from, to) 사이 가장 빠른 경로의 소요 시간과 구간(도보·지하철…)을 줘요. 계획을 볼 수 있는 사람만 불러요. (S4-7a, S4-7b)"),
            op("POST /places", "카드 저장 (여러 장)",
                    "S2-1 \"모두 저장\"과 S3-3 직접 추가가 같이 써요. 장소 카드는 사진·주소·좌표가 필수예요. (S2-1, S3-3)"),
            op("GET /places", "보관함 목록",
                    "카테고리와 위치 반경으로 골라 볼 수 있고, 카테고리별 개수(categoryCounts)를 같이 줘요. (S3-1)"),
            op("GET /places/{placeId}", "카드 상세", "카드 하나의 모든 항목을 줘요. 남의 카드는 404예요. (S3-2)"),
            op("PATCH /places/{placeId}", "카드 수정", "카드 내용을 고쳐요. (S2-2, S3-2)"),
            op("DELETE /places/{placeId}", "카드 삭제 (휴지통으로)",
                    "카드를 휴지통으로 옮겨요. 계획에 쓰인 카드면 그 노드·플랜B·숙소는 빼요. 복구해도 계획 자리는 돌아오지 않아요. (S3-2)"),
            op("GET /places/trash", "휴지통 목록",
                    "지운 카드를 최근에 지운 순서로 줘요. totalCount는 \"총 n개의 카드\"에 써요. (Figma 3-3 휴지통 보기)"),
            op("POST /places/trash/restore", "휴지통에서 복구",
                    "고른 카드(placeIds)를 보관함으로 돌려놔요. 내 휴지통에 없는 카드가 섞이면 404이고 아무것도 바뀌지 않아요. (Figma 3-3)"),
            op("DELETE /places/trash", "휴지통에서 완전 삭제",
                    "고른 카드(placeIds)를 완전히 지워요. 되돌릴 수 없어요. 내 휴지통에 없는 카드가 섞이면 404예요. (Figma 3-3)"),
            op("GET /plans", "계획 목록",
                    "owner·status·period로 골라 봐요. 작성 중인 계획(S4-0), 내 계획·공유받은 계획(S5-1)에 써요."),
            op("POST /plans", "계획 만들기",
                    "여행명·기간·도시로 계획을 만들고 1일차~n일차를 자동으로 만들어요. (S4-1)"),
            op("GET /plans/{planId}", "계획 보기",
                    "일자별 노드·플랜B·숙소와 그 도시의 교통·팁 카드를 줘요. 공유가 켜진 계획은 로그인 없이도 볼 수 있어요. (S4-7)"),
            op("PATCH /plans/{planId}", "계획 수정",
                    "여행명·기간·도시를 고쳐요. 기간을 줄이면 없어지는 일자의 노드·플랜B·구역도 같이 지워요."),
            op("DELETE /plans/{planId}", "계획 삭제", "계획을 만든 사람(OWNER)만 지울 수 있어요. (S5-1)"),
            op("POST /plans/{planId}/copies", "계획 복제", "부른 사람의 작성 중(DRAFT) 계획으로 복사해요. (S5-1)"),
            op("PUT /plans/{planId}/stays", "숙소 정하기",
                    "고른 숙소(STAY) 카드 목록으로 통째로 바꿔요. 숙소 없이 건너뛰면 빈 목록을 보내요. (S4-3)"),
            op("PUT /plans/{planId}/days/{dayNumber}/areas", "구역 정하기",
                    "그 날 둘러볼 구역(이름·좌표) 목록을 통째로 바꿔요. (S4-4)"),
            op("PUT /plans/{planId}/days/{dayNumber}", "일자 동선 저장",
                    "그 날 확정한 노드 순서, 플랜B, 완성 여부를 통째로 저장해요. (S4-5)"),
            op("PATCH /plans/{planId}/days/order", "일자 순서 바꾸기", "일자들의 순서를 바꿔요(예: 3일차 ↔ 5일차). (S4-7)"),
            op("POST /plans/{planId}/ai-drafts", "AI 초안 만들기",
                    "보관함에서 계획 도시의 장소 카드를 모아 AI가 숙소와 일자별 동선을 채워요. "
                            + "기존 동선·플랜B는 덮어쓰고, 10초 안팎 걸려요. 요청 본문은 없어요. (S4-2)"),
            op("POST /plans/{planId}/share-links", "공유 링크 만들기",
                    "읽기 전용(READ_ONLY) 또는 함께 편집(EDIT) 링크를 켜요. OWNER만 할 수 있어요. (S4-8)"));

    /** 주소·쿼리 값 이름 → 설명 */
    private static final Map<String, String> PARAMETERS = Map.ofEntries(
            entry("planId", "계획 id"),
            entry("placeId", "카드 id"),
            entry("placeIds", "고른 카드 id들 (예: placeIds=101,102)"),
            entry("sourceId", "링크 분석 요청 id (POST /sources 응답)"),
            entry("dayNumber", "일자 번호 (1부터)"),
            entry("category", "카테고리: FOOD, CAFE, STAY, SIGHTSEEING, ACTIVITY, SHOPPING, TRANSPORT, TIP. 비우면 전체"),
            entry("latitude", "반경 필터 중심 위도 (longitude와 같이 보내요)"),
            entry("longitude", "반경 필터 중심 경도 (latitude와 같이 보내요)"),
            entry("radiusM", "반경(m). 기본 1500"),
            entry("page", "페이지 번호 (0부터)"),
            entry("size", "한 페이지 개수"),
            entry("sortType", "정렬. 지금은 LATEST(최신순)만 있어요"),
            entry("owner", "me(내 계획) 또는 shared(공유받은 계획). 비우면 둘 다"),
            entry("status", "DRAFT(작성 중) 또는 COMPLETED(완성). 비우면 전체"),
            entry("period", "UPCOMING(다가오는 여행) 또는 PAST(지난 여행). 비우면 전체"),
            entry("query", "검색어 (예: 하카타 잇소 후쿠오카)"),
            entry("from", "출발 카드 id"),
            entry("to", "도착 카드 id"),
            entry("departAt", "출발 시각 (ISO 8601, 예: 2026-12-05T10:00:00+09:00). 비우면 지금"));

    @Bean
    public OpenAPI wayviOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wayvi API")
                        .description("""
                                Wayvi 백엔드 API예요. 화면 번호(S0-1 ~ S5-1)와 자세한 요청·응답 규칙은 docs/api-spec.md를 같이 봐 주세요.

                                **써 보는 순서:** 로그인(로컬은 "개발용 로그인")으로 accessToken을 받아 오른쪽 위 Authorize에 넣고, \
                                각 API의 "Try it out" → "Execute"를 눌러요.

                                실패하면 항상 { "code", "message" } 모양으로 와요. code로 분기하고 message는 화면에 그대로 띄우면 돼요.""")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 응답의 accessToken (Bearer 뒤 값만)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /** 문서를 다 만든 뒤 묶음 이름·요약·파라미터 설명을 한국어로 바꿔요. */
    @Bean
    public OpenApiCustomizer koreanDescriptions() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Set<String> usedTags = new LinkedHashSet<>();
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                describe(operation, method.name() + " " + path);
                if (operation.getTags() != null) {
                    operation.setTags(operation.getTags().stream()
                            .map(name -> TAGS.containsKey(name) ? TAGS.get(name).getName() : name)
                            .toList());
                    usedTags.addAll(operation.getTags());
                }
            }));
            // 화면 흐름 순서대로, 표에 없는 새 묶음은 맨 뒤에
            List<Tag> tags = new ArrayList<>();
            TAGS.values().stream().filter(tag -> usedTags.contains(tag.getName())).forEach(tags::add);
            usedTags.stream()
                    .filter(name -> tags.stream().noneMatch(tag -> tag.getName().equals(name)))
                    .forEach(name -> tags.add(new Tag().name(name)));
            openApi.setTags(tags);
        };
    }

    private static void describe(Operation operation, String key) {
        String[] doc = OPERATIONS.get(key);
        if (doc != null) {
            operation.setSummary(doc[0]);
            operation.setDescription(doc[1]);
        }
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                if (parameter.getDescription() == null && PARAMETERS.containsKey(parameter.getName())) {
                    parameter.setDescription(PARAMETERS.get(parameter.getName()));
                }
            }
        }
    }

    private static void tag(String controllerTag, String name, String description) {
        TAGS.put(controllerTag, new Tag().name(name).description(description));
    }

    private static Map.Entry<String, String[]> op(String key, String summary, String description) {
        return entry(key, new String[] {summary, description});
    }
}
