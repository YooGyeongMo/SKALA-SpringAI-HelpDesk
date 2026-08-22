package com.skala.helpdesk.tools;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * N3 — 모든 도구 호출을 감사한다. 성공·실패·소요시간을 전용 로거(AI_TOOL_AUDIT)에 남기고,
 * 개인정보로 보이는 인자는 마스킹한다(감사 로그 자체가 유출 경로가 되면 안 된다).
 * 부수 효과로 ToolCallTracker 에 "도구 사용됨"을 기록해 AnswerDto.toolUsed 의 근거가 된다.
 */
@Aspect
@Component
public class ToolAuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    private final ToolCallTracker tracker;

    public ToolAuditAspect(ToolCallTracker tracker) {
        this.tracker = tracker;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object auditToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String tool = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName();
        String args = mask(Arrays.toString(joinPoint.getArgs()));
        long started = System.nanoTime();

        tracker.markUsed();
        try {
            Object result = joinPoint.proceed();
            audit.info("tool={} args={} status=OK elapsedMs={}",
                    tool, args, (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (Throwable e) {
            audit.error("tool={} args={} status=FAIL error={}", tool, args, e.toString());
            throw e;
        }
    }

    private String mask(String raw) {
        return raw
                .replaceAll("\\d{6}-\\d{7}", "******-*******")
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "***@***");
    }
}
