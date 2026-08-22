package com.skala.helpdesk.rag;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Phase 2 — 사내 규정 문서를 읽어 청크로 나누고 메타데이터를 붙여 벡터 저장소에 넣는다.
 *
 * 재색인 없이 add 만 반복하면 같은 청크가 쌓인다 — 문서 단위 삭제(delete)를 먼저 한다.
 * 메타데이터는 인제스트 시점에만 넣을 수 있다 — 출처 표기용 source·version 은 필수.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourceResolver;

    public IngestService(VectorStore vectorStore, ResourcePatternResolver resourceResolver) {
        this.vectorStore = vectorStore;
        this.resourceResolver = resourceResolver;
    }

    public record IngestResult(String source, int chunks) {}

    /** classpath:policies/ 의 규정 문서 전체를 (재)색인한다. */
    public List<IngestResult> ingestPolicies() {
        try {
            Resource[] files = resourceResolver.getResources("classpath:policies/*.md");
            List<IngestResult> results = new ArrayList<>();
            for (Resource file : files) {
                results.add(ingest(file, "policy", "CS"));
            }
            return results;
        } catch (IOException e) {
            throw new IllegalStateException("규정 문서를 읽을 수 없다: " + e.getMessage(), e);
        }
    }

    public IngestResult ingest(Resource file, String docType, String dept) {
        String source = file.getFilename() == null ? "unknown" : file.getFilename();

        deleteExisting(source);                                  // ① 재색인 대비 — 문서 단위 삭제

        List<Document> raw = new TikaDocumentReader(file).get(); // ② Read — Tika 가 PDF·DOCX·MD 모두 처리

        List<Document> chunks = TokenTextSplitter.builder()      // ③ Split — 질문 하나에 답할 분량
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withKeepSeparator(true)
                .build()
                .apply(raw);

        List<Document> enriched = chunks.stream()                // ④ Enrich — 출처 표기·필터용 메타데이터
                .map(chunk -> {
                    Map<String, Object> meta = new HashMap<>(chunk.getMetadata());
                    meta.put("source", source);
                    meta.put("docType", docType);
                    meta.put("dept", dept);
                    meta.put("version", LocalDate.now().toString());
                    return new Document(chunk.getText(), meta);
                })
                .toList();

        vectorStore.add(enriched);                               // ⑤ Write — 임베딩 + 저장
        log.info("인제스트 완료 source={} chunks={}", source, enriched.size());

        return new IngestResult(source, enriched.size());
    }

    private void deleteExisting(String source) {
        try {
            vectorStore.delete("source == '" + source + "'");
        } catch (Exception e) {
            log.debug("기존 청크 삭제를 건너뛴다: {}", e.getMessage());
        }
    }
}
