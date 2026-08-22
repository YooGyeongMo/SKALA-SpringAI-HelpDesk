package com.skala.helpdesk.chat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.skala.helpdesk.config.HelpDeskProperties;
import com.skala.helpdesk.ops.SemanticCacheService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.helpdesk.tools.OrderTools;
import com.skala.helpdesk.tools.TicketTools;
import com.skala.helpdesk.tools.ToolCallTracker;

import reactor.core.publisher.Flux;

/**
 * Phase 3·5 — 업무 흐름. 컨트롤러는 AI 를 모르고, 이 서비스만 ChatClient 를 안다.
 *
 * Advisor(QA)가 근거를 넣어 주지만, 출처는 응답 컨텍스트(RETRIEVED_DOCUMENTS)에서
 * 우리가 꺼내 붙여야 한다. 근거가 없으면 시스템 프롬프트가 "확인되지 않습니다"로 답하게 한다.
 */
@Service
public class HelpDeskService {

    private static final String TENANT = "skala";

    private static final Logger log = LoggerFactory.getLogger(HelpDeskService.class);

    private final ChatClient chat;
    private final OrderTools orderTools;
    private final TicketTools ticketTools;
    private final ToolCallTracker toolCallTracker;
    private final ObjectMapper objectMapper;
    private final HelpDeskProperties props;
    private final SemanticCacheService cache;
    private final ChatMemory chatMemory;

    public HelpDeskService(ChatClient helpDeskClient, OrderTools orderTools,
                           TicketTools ticketTools, ToolCallTracker toolCallTracker,
                           ObjectMapper objectMapper, HelpDeskProperties props,
                           SemanticCacheService cache, ChatMemory chatMemory) {
        this.chat = helpDeskClient;
        this.orderTools = orderTools;
        this.ticketTools = ticketTools;
        this.toolCallTracker = toolCallTracker;
        this.objectMapper = objectMapper;
        this.props = props;
        this.cache = cache;
        this.chatMemory = chatMemory;
    }

    /**
     * N4 — 주 모델 장애 시에도 응답을 지속한다.
     * 일시 오류는 3회 재시도(1s→2s→4s), 소진되면 fallback() 이 폴백 모델로 이어받는다.
     * (@Retryable 동작에는 메인 클래스의 @EnableRetry 가 필수 — 샘플 ch12 누락 함정 보완)
     */
    @Retryable(
            retryFor = { TransientAiException.class, java.io.IOException.class },
            noRetryFor = IllegalArgumentException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public AnswerDto ask(String question, String userId, String sessionId) {
        // 시맨틱 캐시는 대화 맥락이 없는 첫 턴에만 — "그럼 그거…" 같은 질문이 남의 맥락 답을 받으면 사고다.
        boolean firstTurn = chatMemory.get(conversationId(userId, sessionId)).isEmpty();
        if (firstTurn) {
            var hit = cache.lookup(question);
            if (hit.isPresent()) {
                return hit.get();
            }
        }

        AnswerDto answer = doAsk(question, userId, sessionId, null);

        if (firstTurn) {
            cache.put(question, answer);   // 도구 사용 답변은 put 내부에서 거른다
        }
        return answer;
    }

    /** 주 모델이 끝내 실패하면 폴백 모델로 한 번 더 — 그마저 실패하면 안전 문구로 화면은 살린다. */
    @Recover
    public AnswerDto fallback(Exception e, String question, String userId, String sessionId) {
        log.error("주 모델 호출 실패 — 폴백 모델({})로 전환한다: {}", props.fallback().model(), e.toString());
        try {
            AnswerDto answer = doAsk(question, userId, sessionId,
                    ChatOptions.builder().model(props.fallback().model()).build());
            log.info("폴백 모델 응답 성공 model={}", props.fallback().model());
            return answer;
        } catch (Exception fallbackError) {
            log.error("폴백 모델도 실패", fallbackError);
            return new AnswerDto("지금은 답변을 드리기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    List.of(), false, false);
        }
    }

    private AnswerDto doAsk(String question, String userId, String sessionId, ChatOptions options) {
        toolCallTracker.reset();

        var spec = chat.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(userId, sessionId)))
                .tools(orderTools, ticketTools)
                .toolContext(Map.of("userId", userId));  // 신원은 프롬프트가 아니라 이 경로로만
        if (options != null) {
            spec = spec.options(options);                // 호출 단위 모델 교체(폴백)
        }
        ChatClientResponse response = spec.call().chatClientResponse();   // 응답 + Advisor 컨텍스트

        String text = response.chatResponse().getResult().getOutput().getText();
        return new AnswerDto(text, extractSources(response), toolCallTracker.consumeUsed(), false);
    }

    /**
     * Phase 6 — SSE 스트리밍. 토큰(token)을 흘려보내고, 스트림이 끝나면 출처(sources)를
     * 마지막 이벤트로 함께 내보낸다 — 스트리밍 응답에도 출처는 빠지지 않는다.
     */
    public record StreamChunk(String type, String data) {}

    public Flux<StreamChunk> stream(String question, String userId, String sessionId) {
        AtomicReference<List<AnswerDto.Source>> sources = new AtomicReference<>(List.of());

        return chat.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(userId, sessionId)))
                .tools(orderTools, ticketTools)
                .toolContext(Map.of("userId", userId))
                .stream()
                .chatClientResponse()
                .doOnNext(r -> {                       // 컨텍스트에 근거가 실려 오면 잡아 둔다
                    List<AnswerDto.Source> found = extractSources(r);
                    if (!found.isEmpty()) {
                        sources.set(found);
                    }
                })
                .mapNotNull(this::deltaText)
                .filter(t -> !t.isEmpty())
                .map(t -> new StreamChunk("token", t))
                .concatWith(Flux.defer(() -> Flux.just(
                        new StreamChunk("sources", toJson(sources.get())),
                        new StreamChunk("done", "[DONE]"))));
    }

    private String deltaText(ChatClientResponse r) {
        if (r.chatResponse() == null || r.chatResponse().getResult() == null
                || r.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return r.chatResponse().getResult().getOutput().getText();
    }

    private String toJson(List<AnswerDto.Source> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /** QA Advisor 가 실제로 사용한 근거 문서 목록을 컨텍스트에서 꺼낸다. */
    @SuppressWarnings("unchecked")
    private List<AnswerDto.Source> extractSources(ChatClientResponse response) {
        List<Document> used = (List<Document>) response.context()
                .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (used == null) {
            return List.of();
        }
        return used.stream()
                .map(d -> new AnswerDto.Source(
                        String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("version"))))
                .distinct()
                .toList();
    }

    /**
     * 대화 ID 규칙 — 테넌트·사용자·세션을 한 곳에서 만든다.
     * 잘못 만들면 남의 대화가 섞인다: 사용자별·세션별로 반드시 격리.
     */
    public String conversationId(String userId, String sessionId) {
        return "%s:%s:%s".formatted(TENANT, userId, sessionId);
    }
}
