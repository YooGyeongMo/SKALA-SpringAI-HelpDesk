package com.skala.helpdesk.web;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 실패해도 화면은 살린다 — 사용자에게는 안전 문구 + traceId 만, 상세(스택트레이스·프롬프트)는 로그에만.
 * (샘플 lab14 의 basePackages 오타 함정을 피해 실제 패키지로 지정한다.)
 */
@RestControllerAdvice(basePackages = "com.skala.helpdesk.web")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().isEmpty()
                ? "요청" : e.getBindingResult().getFieldErrors().get(0).getField();
        return ResponseEntity.badRequest().body(Map.of(
                "message", field + " 값을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> failure(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] 처리 실패", traceId, e);
        return ResponseEntity.status(503).body(Map.of(
                "message", "잠시 후 다시 시도해 주세요.",
                "traceId", traceId));
    }
}
