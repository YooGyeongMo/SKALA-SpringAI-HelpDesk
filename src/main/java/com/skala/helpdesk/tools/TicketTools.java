package com.skala.helpdesk.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.repository.OrderRepository;
import com.skala.helpdesk.repository.TicketRepository;

/**
 * Phase 4 — 되돌리기 어려운 행동(교환·환불)은 접수만 한다.
 * 처리는 담당자가 승인 API(도구 목록에 없음)로 확정한다 — 모델은 승인을 부를 수 없다.
 */
@Component
public class TicketTools {

    private static final Logger log = LoggerFactory.getLogger(TicketTools.class);

    private final TicketRepository tickets;
    private final OrderRepository orders;

    public TicketTools(TicketRepository tickets, OrderRepository orders) {
        this.tickets = tickets;
        this.orders = orders;
    }

    @Tool(description = "교환 또는 환불 티켓을 접수한다. 사용자가 교환·환불·반품을 요청할 때 사용한다. 즉시 처리되지 않으며 담당자 승인 후 진행된다. 본인 주문만 접수할 수 있다.")
    public String createTicket(
            @ToolParam(description = "주문번호 (숫자 5자리, 예: 12345)") String orderId,
            @ToolParam(description = "요청 종류: EXCHANGE(교환) 또는 REFUND(환불)") String type,
            @ToolParam(description = "요청 사유 (예: 사이즈 변경, 단순 변심)") String reason,
            ToolContext context) {

        String userId = OrderTools.currentUser(context);

        // 소유자 검증은 쓰기 도구에서 더 중요하다 — 남의 주문으로 티켓을 만들 수 없다.
        if (orders.findOwned(orderId, userId).isEmpty()) {
            return "해당 주문을 찾을 수 없습니다.";
        }

        TicketRepository.Ticket t = tickets.request(orderId, type, reason, userId);
        log.debug("[TOOL] createTicket no={} orderId={} type={} by={}", t.no(), orderId, type, userId);
        return "티켓 %s 를 접수했습니다. 담당자 승인 후 처리됩니다.".formatted(t.no());
    }

    @Tool(description = "내가 접수한 교환·환불 티켓 목록과 승인 상태를 조회한다. 사용자가 티켓 진행 상황을 물을 때 사용한다.")
    public String myTickets(ToolContext context) {
        String userId = OrderTools.currentUser(context);
        var mine = tickets.findByUser(userId);
        if (mine.isEmpty()) {
            return "접수된 티켓이 없습니다.";
        }
        StringBuilder sb = new StringBuilder("접수된 티켓 목록:\n");
        mine.forEach(t -> sb.append("- %s · 주문 %s · %s · 사유 %s · 상태 %s\n"
                .formatted(t.no(), t.orderId(), t.type(), t.reason(),
                        t.status() == TicketRepository.Status.PENDING ? "승인 대기중" : "승인 완료")));
        return sb.toString();
    }
}
