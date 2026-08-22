package com.skala.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI helpDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SKALA HelpDesk AI — 종합실습")
                        .version("1.0.0")
                        .description("""
                                사내 규정(RAG) · 실시간 주문/티켓(Tool) · 멀티턴 메모리 · 안전(SafeGuard/감사) · 관찰(Micrometer)을 \
                                하나로 조립한 상담 어시스턴트.

                                **검증 다섯 흐름**
                                1. 규정 질문 → 근거 + 출처 (RAG)
                                2. "내 주문은?" → 메모리 + @Tool 주문 조회
                                3. "교환 접수해줘" → 승인 게이트(PENDING) → 사람이 승인
                                4. 레드팀 10종 방어 + 모든 도구 호출 감사 로그
                                5. 주 모델 장애 주입 → 폴백으로 응답 지속

                                사용자 식별은 `X-User-Id` 헤더(기본 user1). 시드 주문: 12345·12346·12347=user1, 99999=user2.
                                """)
                        .contact(new Contact().name("SKALA SpringAI 과정 — 종합실습")))
                .addServersItem(new Server().url("http://localhost:8080").description("로컬 실행"));
    }
}
