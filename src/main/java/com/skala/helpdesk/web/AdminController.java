package com.skala.helpdesk.web;

import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.rag.IngestService;

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

    public AdminController(IngestService ingestService, VectorStore vectorStore) {
        this.ingestService = ingestService;
        this.vectorStore = vectorStore;
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
}
