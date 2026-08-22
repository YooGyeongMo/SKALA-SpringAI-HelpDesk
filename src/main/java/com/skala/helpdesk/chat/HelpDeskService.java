package com.skala.helpdesk.chat;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Phase 3·5 — 업무 흐름. 컨트롤러는 AI 를 모르고, 이 서비스만 ChatClient 를 안다.
 *
 * Advisor(QA)가 근거를 넣어 주지만, 출처는 응답 컨텍스트(RETRIEVED_DOCUMENTS)에서
 * 우리가 꺼내 붙여야 한다. 근거가 없으면 시스템 프롬프트가 "확인되지 않습니다"로 답하게 한다.
 */
@Service
public class HelpDeskService {

    private static final String TENANT = "skala";

    private final ChatClient chat;

    public HelpDeskService(ChatClient helpDeskClient) {
        this.chat = helpDeskClient;
    }

    public AnswerDto ask(String question, String userId, String sessionId) {
        ChatClientResponse response = chat.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(userId, sessionId)))
                .call()
                .chatClientResponse();               // 응답 + Advisor 컨텍스트

        String text = response.chatResponse().getResult().getOutput().getText();
        return new AnswerDto(text, extractSources(response), false);
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
    private String conversationId(String userId, String sessionId) {
        return "%s:%s:%s".formatted(TENANT, userId, sessionId);
    }
}
