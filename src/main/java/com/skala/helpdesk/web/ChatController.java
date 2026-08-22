package com.skala.helpdesk.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Phase 6 — REST 진입점. 받고·검증하고·돌려줄 뿐, AI 는 Service 뒤에 숨긴다.
 * 사용자 식별은 X-User-Id 헤더(과제 스코프에서 인증 대체) — 프롬프트로는 절대 넘기지 않는다.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "0. Chat — HelpDesk 상담", description = "규정(RAG)·주문(Tool)·멀티턴(Memory)을 한 흐름으로 처리")
public class ChatController {

    private final HelpDeskService service;

    public ChatController(HelpDeskService service) {
        this.service = service;
    }

    public record AskRequest(
            @Schema(example = "단순 변심 반품은 며칠 이내에 해야 하나요?") @NotBlank String question,
            @Schema(example = "session-1") @NotBlank String sessionId) {}

    @PostMapping
    @Operation(summary = "동기 질문 — 구조화 응답",
            description = "답변·출처·도구 사용 여부를 함께 반환한다. 같은 sessionId 로 이어 물으면 앞 대화 맥락이 유지된다.")
    public AnswerDto ask(@Valid @RequestBody AskRequest req,
                         @Parameter(description = "사용자 ID (인증 대체)", example = "user1")
                         @RequestHeader(value = "X-User-Id", defaultValue = "user1") String userId) {
        return service.ask(req.question(), userId, req.sessionId());
    }
}
