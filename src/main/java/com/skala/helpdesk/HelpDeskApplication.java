package com.skala.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;

/**
 * SKALA HelpDesk AI — 사내 규정(RAG)과 실시간 데이터(Tool)를 함께 다루는 상담 어시스턴트.
 * RAG · Tool · 메모리 · 안전 · 관찰을 하나의 요청 흐름으로 조립한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRetry   // 주 모델 장애 시 @Retryable → @Recover 폴백이 동작하기 위한 필수 스위치
public class HelpDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }
}
