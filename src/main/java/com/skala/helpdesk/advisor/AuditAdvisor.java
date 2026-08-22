package com.skala.helpdesk.advisor;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * 감사 로깅 — 모든 모델 호출의 시작·성공·실패를 traceId 로 관통해 기록한다.
 *
 * order 0 = 체인의 가장 바깥. 안쪽(SafeGuard·Memory·QA·모델·도구) 전체가 감사 범위에 들어온다.
 * CallAdvisor 만 구현하면 스트리밍 경로에서 건너뛰어지므로 StreamAdvisor 도 함께 구현한다.
 */
@Component
public class AuditAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger audit = LoggerFactory.getLogger("AI_CHAT_AUDIT");

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String traceId = newTraceId();
        long started = System.nanoTime();
        audit.info("[{}] 요청 질문=\"{}\"", traceId, summarize(request));
        try {
            ChatClientResponse response = chain.nextCall(request);
            audit.info("[{}] 완료 status=OK elapsedMs={}", traceId, elapsedMs(started));
            return response;
        } catch (RuntimeException e) {
            audit.warn("[{}] 실패 status=FAIL elapsedMs={} error={}", traceId, elapsedMs(started), e.toString());
            throw e;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String traceId = newTraceId();
        long started = System.nanoTime();
        audit.info("[{}] 스트림 요청 질문=\"{}\"", traceId, summarize(request));
        return chain.nextStream(request)
                .doOnComplete(() -> audit.info("[{}] 스트림 완료 status=OK elapsedMs={}", traceId, elapsedMs(started)))
                .doOnError(e -> audit.warn("[{}] 스트림 실패 status=FAIL elapsedMs={} error={}",
                        traceId, elapsedMs(started), e.toString()))
                .doOnCancel(() -> audit.warn("[{}] 스트림 취소 elapsedMs={}", traceId, elapsedMs(started)));
    }

    /** 감사 로그에 개인정보 원문이 남지 않게 마스킹하고 80자로 줄인다. */
    private String summarize(ChatClientRequest request) {
        String text = request.prompt().getContents();
        String masked = text
                .replaceAll("\\d{6}-\\d{7}", "******-*******")
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****");
        return masked.length() > 80 ? masked.substring(0, 80) + "…" : masked;
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    @Override
    public String getName() {
        return "audit";
    }

    /** 가장 바깥 — 차단·저장·검색 무엇이 일어나든 감사에는 남는다. */
    @Override
    public int getOrder() {
        return 0;
    }
}
