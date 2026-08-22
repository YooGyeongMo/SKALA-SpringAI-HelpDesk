package com.skala.helpdesk.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.skala.helpdesk.advisor.AuditAdvisor;

/**
 * Phase 1 — ChatClient 하나에 Advisor 체인 전체를 기본값으로 걸어 둔다.
 *
 * 요청: Audit(0) → SafeGuard(100) → Memory(200) → QA(300) → Logger → 모델 / 응답은 역순.
 * 안전 필터를 메모리보다 뒤에 두면 걸러야 할 문장이 이미 대화 이력에 저장된다 —
 * 차단은 언제나 저장보다 앞이다.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository, HelpDeskProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(props.memory().maxMessages())   // 길어진 대화는 윈도우로 잘라 토큰 통제
                .build();
    }

    @Bean
    public ChatClient helpDeskClient(ChatClient.Builder builder,
                                     VectorStore vectorStore,
                                     ChatMemory chatMemory,
                                     HelpDeskProperties props,
                                     AuditAdvisor audit,
                                     @Value("classpath:prompts/system.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        audit,                                               // order 0   — 감사(가장 바깥)
                        SafeGuardAdvisor.builder()                           // order 100 — 입력 차단
                                .sensitiveWords(List.of("주민등록번호", "카드번호", "비밀번호"))
                                .failureResponse("죄송합니다. 민감정보가 포함된 요청은 처리할 수 없습니다.")
                                .order(100)
                                .build(),
                        MessageChatMemoryAdvisor.builder(chatMemory)         // order 200 — 맥락 주입
                                .order(200)
                                .build(),
                        QuestionAnswerAdvisor.builder(vectorStore)           // order 300 — 근거 주입
                                .searchRequest(SearchRequest.builder()
                                        .topK(props.rag().topK())
                                        .similarityThreshold(props.rag().threshold())
                                        .build())
                                .order(300)
                                .build(),
                        new SimpleLoggerAdvisor())                           // 모델로 나가는 최종 요청 로깅
                .build();
    }
}
