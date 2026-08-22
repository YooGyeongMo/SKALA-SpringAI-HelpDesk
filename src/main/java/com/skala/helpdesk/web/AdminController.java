package com.skala.helpdesk.web;

import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.ai.chat.memory.ChatMemory;

import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.rag.IngestService;
import com.skala.helpdesk.repository.TicketRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 운영자용 API — 인제스트는 성공 메시지가 아니라 결과물(청크)로 확인한다.
 * 확인 창구(/chunks)를 열어 두면 Phase 3 이후 RAG 가 틀렸을 때 원인을 검색 단계부터 눈으로 찾을 수 있다.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "1. Admin — 인제스트·검색 확인", description = "규정 문서 색인과 검색 품질 확인 (운영자 전용)")
public class AdminController {

    private final IngestService ingestService;
    private final VectorStore vectorStore;
    private final TicketRepository tickets;
    private final ChatMemory chatMemory;
    private final HelpDeskService helpDeskService;

    public AdminController(IngestService ingestService, VectorStore vectorStore,
                           TicketRepository tickets, ChatMemory chatMemory, HelpDeskService helpDeskService) {
        this.ingestService = ingestService;
        this.vectorStore = vectorStore;
        this.tickets = tickets;
        this.chatMemory = chatMemory;
        this.helpDeskService = helpDeskService;
    }

    @PostMapping("/ingest")
    @Operation(summary = "규정 문서 전체 (재)색인",
            description = "classpath:policies/*.md 를 문서 단위 삭제 후 다시 넣는다. 두 번 실행해도 청크 수가 같아야 정상(재색인).")
    public List<IngestService.IngestResult> ingest() {
        return ingestService.ingestPolicies();
    }

    @GetMapping("/chunks")
    @Operation(summary = "검색 결과를 눈으로 확인",
            description = "질문에 대해 어떤 청크가 몇 점으로 검색되는지 본다. score 는 유사도 임계값(helpdesk.rag.threshold) 조정의 근거다.")
    public List<Map<String, Object>> inspect(
            @Parameter(description = "검색 질문", example = "반품 배송비는 누가 내나요") @RequestParam String q,
            @Parameter(description = "상위 몇 건", example = "5") @RequestParam(defaultValue = "5") int topK) {

        var hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(q).topK(topK).build());

        return hits.stream().map(d -> Map.<String, Object>of(
                "source", String.valueOf(d.getMetadata().get("source")),
                "version", String.valueOf(d.getMetadata().get("version")),
                "score", d.getScore(),
                "preview", d.getText().substring(0, Math.min(160, d.getText().length()))
        )).toList();
    }

    // ── 승인 게이트 — 승인은 사람만. 이 엔드포인트는 도구 목록에 없으므로 모델이 부를 수 없다. ──

    @GetMapping("/tickets/pending")
    @Operation(summary = "승인 대기 티켓 목록", description = "도구가 접수(PENDING)한 교환·환불 티켓을 담당자가 확인한다.")
    public List<TicketRepository.Ticket> pendingTickets() {
        return tickets.pending();
    }

    @PostMapping("/tickets/{no}/approve")
    @Operation(summary = "티켓 승인 (사람 전용)",
            description = "되돌리기 어려운 행동의 확정은 사람이 한다 — 승인 게이트의 마지막 단계.")
    public ResponseEntity<TicketRepository.Ticket> approve(
            @Parameter(description = "티켓 번호", example = "T-0001") @PathVariable String no) {
        return tickets.approve(no)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    @Operation(summary = "대화 이력 확인 (메모리 검증 창구)",
            description = "해당 사용자·세션의 메모리에 실제로 무엇이 저장됐는지 본다. 멀티턴·격리 검증의 근거.")
    public List<Map<String, String>> history(
            @Parameter(example = "user1") @RequestParam(defaultValue = "user1") String userId,
            @Parameter(example = "session-1") @RequestParam String sessionId) {
        return chatMemory.get(helpDeskService.conversationId(userId, sessionId)).stream()
                .map(m -> Map.of(
                        "role", m.getMessageType().getValue(),
                        "text", m.getText()))
                .toList();
    }
}
