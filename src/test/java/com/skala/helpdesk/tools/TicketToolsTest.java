package com.skala.helpdesk.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.skala.helpdesk.repository.OrderRepository;
import com.skala.helpdesk.repository.TicketRepository;

class TicketToolsTest {

    private final TicketRepository tickets = new TicketRepository();
    private final TicketTools tools = new TicketTools(tickets, new OrderRepository());

    @Test
    @DisplayName("본인 주문 티켓은 접수되고 상태는 PENDING 으로 시작한다 — 승인 게이트")
    void 티켓은_접수만_된다() {
        String result = tools.createTicket("12345", "EXCHANGE", "사이즈 변경",
                new ToolContext(Map.of("userId", "user1")));
        assertThat(result).contains("접수").contains("승인 후 처리");
        assertThat(tickets.pending()).hasSize(1);
        assertThat(tickets.pending().get(0).status()).isEqualTo(TicketRepository.Status.PENDING);
    }

    @Test
    @DisplayName("남의 주문으로는 티켓을 만들 수 없다 — 쓰기 도구의 소유자 검증")
    void 남의_주문은_접수되지_않는다() {
        String result = tools.createTicket("99999", "REFUND", "단순 변심",
                new ToolContext(Map.of("userId", "user1")));
        assertThat(result).isEqualTo("해당 주문을 찾을 수 없습니다.");
        assertThat(tickets.pending()).isEmpty();
    }

    @Test
    @DisplayName("승인은 저장소 API 로만 가능하고 상태가 APPROVED 로 바뀐다")
    void 승인은_사람의_API_로만() {
        tools.createTicket("12345", "EXCHANGE", "사이즈", new ToolContext(Map.of("userId", "user1")));
        String no = tickets.pending().get(0).no();

        var approved = tickets.approve(no);

        assertThat(approved).isPresent();
        assertThat(approved.get().status()).isEqualTo(TicketRepository.Status.APPROVED);
        assertThat(tickets.pending()).isEmpty();
    }
}
