package com.skala.helpdesk.tools;

import org.springframework.stereotype.Component;

/**
 * 이번 요청에서 도구가 호출됐는지 추적한다 — AnswerDto.toolUsed 의 근거.
 * 동기 호출 경로에서 도구는 요청 스레드에서 실행되므로 ThreadLocal 로 충분하다.
 */
@Component
public class ToolCallTracker {

    private static final ThreadLocal<Boolean> USED = ThreadLocal.withInitial(() -> false);

    public void reset() {
        USED.set(false);
    }

    public void markUsed() {
        USED.set(true);
    }

    /** 읽고 정리 — 스레드 풀 재사용 시 이전 요청 값이 남지 않게 remove 까지 한다. */
    public boolean consumeUsed() {
        boolean used = USED.get();
        USED.remove();
        return used;
    }
}
