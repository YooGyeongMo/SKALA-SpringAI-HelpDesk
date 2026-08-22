package com.skala.helpdesk.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.repository.OrderRepository;

/**
 * Phase 4 — 문서로 답할 수 없는 실시간 주문 상태는 도구로 가져온다.
 *
 * 원칙 3가지:
 * ① description 이 곧 모델의 사용 설명서 — 언제 쓰는지까지 적는다.
 * ② 예외 대신 사람이 읽을 메시지를 반환한다.
 * ③ 모델이 넘긴 orderId 는 사용자의 것이 아닐 수 있다 — 소유자 검증은 도구 안에서.
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private final OrderRepository orders;

    public OrderTools(OrderRepository orders) {
        this.orders = orders;
    }

    @Tool(description = "주문번호로 배송 상태와 도착(예정)일을 조회한다. 사용자가 자기 주문의 위치·상태·도착일을 물을 때 사용한다. 본인 주문만 조회된다.")
    public String orderStatus(
            @ToolParam(description = "주문번호 (숫자 5자리, 예: 12345)") String orderId,
            ToolContext context) {

        String userId = currentUser(context);
        log.debug("[TOOL] orderStatus orderId={} by={}", orderId, userId);

        return orders.findOwned(orderId, userId)
                .map(o -> "주문 %s · 상품 %s · 상태 %s · %s %s".formatted(
                        o.id(), o.item(), o.status().label(),
                        o.status() == OrderRepository.Status.DELIVERED ? "배송완료일" : "예상도착일",
                        o.eta()))
                // 없는 주문과 남의 주문을 구분해 알려 주면 그 자체가 정보 노출이다 — 같은 문구로.
                .orElse("해당 주문을 찾을 수 없습니다.");
    }

    /** 사용자 ID 는 프롬프트가 아니라 ToolContext 로 온다 — 모델이 바꿔 부를 수 없는 경로다. */
    static String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("toolContext 에 userId 가 없다 — 호출부 설정을 확인하라");
        }
        return userId.toString();
    }
}
