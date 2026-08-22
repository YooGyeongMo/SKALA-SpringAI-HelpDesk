package com.skala.helpdesk.ops;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.config.HelpDeskProperties;

/**
 * 시맨틱 캐시 — 뜻이 같은 질문은 모델을 부르지 않고 즉시 응답한다 (비용·지연 절감).
 *
 * 규칙:
 * - 임계값(기본 0.95)은 아주 높게 — 애매하면 캐시를 쓰지 않는 편이 안전하다.
 * - 도구를 쓴 답변(개인화·실시간)은 절대 캐시하지 않는다.
 * - 대화 맥락이 있는 질문("그럼 그거…")도 캐시 대상이 아니다 — 호출부가 첫 턴에만 캐시를 쓴다.
 * - 캐시 저장소는 규정 벡터와 분리된 전용 인메모리 스토어를 쓴다.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final SimpleVectorStore cacheStore;
    private final double threshold;
    private final ObjectMapper objectMapper;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public SemanticCacheService(EmbeddingModel embeddingModel, HelpDeskProperties props,
                                ObjectMapper objectMapper) {
        this.cacheStore = SimpleVectorStore.builder(embeddingModel).build();
        this.threshold = props.cache().threshold();
        this.objectMapper = objectMapper;
    }

    public Optional<AnswerDto> lookup(String question) {
        List<Document> found = cacheStore.similaritySearch(SearchRequest.builder()
                .query(question).topK(1).similarityThreshold(0).build());
        Double score = (found == null || found.isEmpty()) ? null : found.get(0).getScore();
        log.debug("캐시 조회 질문=\"{}\" 최고점수={} 임계값={}", question, score, threshold);

        if (found == null || found.isEmpty() || score == null || score < threshold) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        Map<String, Object> meta = found.get(0).getMetadata();
        log.info("시맨틱 캐시 적중(score={}) — 원 질문=\"{}\"", score, found.get(0).getText());
        return Optional.of(new AnswerDto(
                String.valueOf(meta.get("answer")),
                readSources(String.valueOf(meta.get("sources"))),
                false, true));
    }

    public void put(String question, AnswerDto answer) {
        if (answer.toolUsed()) {
            return;   // 개인화·실시간 답변은 캐시 금지 — 남의 주문 정보가 캐시로 새는 사고를 원천 차단
        }
        try {
            cacheStore.add(List.of(new Document(question, Map.of(
                    "answer", answer.answer(),
                    "sources", objectMapper.writeValueAsString(answer.sources()),
                    "cachedAt", Instant.now().toString()))));
        } catch (Exception e) {
            log.debug("캐시 저장 건너뜀: {}", e.getMessage());
        }
    }

    public Map<String, Object> stats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return Map.of(
                "hits", h,
                "misses", m,
                "hitRatePercent", total == 0 ? 0 : Math.round(h * 100.0 / total));
    }

    private List<AnswerDto.Source> readSources(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<AnswerDto.Source>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
