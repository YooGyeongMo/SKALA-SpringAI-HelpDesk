package com.skala.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공급자·모델·임계값을 전부 설정으로 뺀다 — 코드에 상수를 남기지 않는다.
 * application.yml 의 helpdesk.* 에 바인딩된다.
 */
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(Rag rag, Memory memory, Fallback fallback) {

    /** RAG 검색 — 상위 몇 건을, 어느 유사도 이상만 근거로 쓸지 */
    public record Rag(int topK, double threshold) {}

    /** 대화 메모리 — 길어진 대화는 윈도우로 잘라 토큰을 통제한다 */
    public record Memory(int maxMessages) {}

    /** 주 모델 장애 시 전환할 폴백 모델 */
    public record Fallback(String model) {}
}
