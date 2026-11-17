package com.hanol.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 코드에서 자동 생성되는 API 문서(/v3/api-docs) 설정. 설계용 YAML 명세(static/docs/openapi.yaml)와 함께 Swagger UI 상단
 * 드롭다운에서 골라 볼 수 있다.
 */
@Configuration
public class SwaggerConfig {

  private static final String BEARER_AUTH = "bearerAuth";

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info().title("PATCH API").description("2026 간지톤 26팀 'PATCH' 백엔드 API").version("v1"))
        .tags(
            List.of(
                new Tag().name("USR").description("유저, 인증"),
                new Tag().name("CRD").description("마음 기록, 감정 도안"),
                new Tag().name("BLK").description("패치 선물하기/받기, 담요"),
                new Tag().name("ITR").description("온기 지도, 함께 쉬는 방"),
                new Tag().name("ALRT").description("도움 요청")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
