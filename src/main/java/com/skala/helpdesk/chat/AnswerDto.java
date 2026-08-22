package com.skala.helpdesk.chat;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Phase 6 스펙의 구조화 응답 — 답변만 던지지 않는다.
 * 화면이 쓰기 좋게 답변·출처·도구 사용 여부를 나눠 반환한다.
 */
public record AnswerDto(
        @Schema(example = "단순 변심 반품은 배송 완료일로부터 7일 이내에 신청하실 수 있습니다.") String answer,
        @Schema(description = "답변 근거 문서 — 비어 있으면 문서 근거 없이 답한 것") List<Source> sources,
        @Schema(description = "이번 답변에서 도구(주문 조회·티켓 접수)가 호출됐는지") boolean toolUsed,
        @Schema(description = "시맨틱 캐시 적중 여부 — true 면 모델을 부르지 않고 즉시 응답한 것") boolean cached) {

    public record Source(
            @Schema(example = "반품규정.md") String document,
            @Schema(example = "2026-08-22") String version) {}
}
