// BE 2가 추가 (2026-11-14). Swagger로 FE가 API를 보고 바로 호출해 볼 수 있게 해요.
package com.reeltrip.global.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.reeltrip.global.security.LoginUser;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI 우측 위 "Authorize"에 액세스 토큰(Bearer 뒤 값)만 넣으면 모든 요청에 붙어요.
 * 로그인이 필요 없는 /auth/** 등도 자물쇠가 보이지만 토큰 없이 불러도 돼요.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    static {
        // @LoginUser 회원 id는 토큰에서 꺼내는 값이라 요청 파라미터로 보이지 않게 해요.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);
    }

    @Bean
    public OpenAPI wayviOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wayvi API")
                        .description("화면 번호(S0-1 ~ S5-1)와 요청·응답 규칙은 docs/api-spec.md를 같이 봐 주세요.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
