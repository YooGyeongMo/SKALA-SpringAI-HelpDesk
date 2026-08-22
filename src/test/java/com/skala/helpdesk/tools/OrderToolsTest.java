package com.skala.helpdesk.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.skala.helpdesk.repository.OrderRepository;

/**
 * 도구는 모델 없이도 테스트할 수 있다 — 그리고 반드시 해야 한다.
 * 권한 격리(소유자 검증)는 확률이 아니라 코드로 보장되는지 확인한다.
 */
class OrderToolsTest {

    private final OrderTools tools = new OrderTools(new OrderRepository());

    @Test
    @DisplayName("본인 주문은 상태가 조회된다")
    void 본인_주문은_조회된다() {
        String result = tools.orderStatus("12345", new ToolContext(Map.of("userId", "user1")));
        assertThat(result).contains("12345").contains("배송완료");
    }

    @Test
    @DisplayName("타인의 주문번호를 대면 조회되지 않는다")
    void 타인_주문은_조회되지_않는다() {
        String result = tools.orderStatus("99999", new ToolContext(Map.of("userId", "user1")));
        assertThat(result).isEqualTo("해당 주문을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("없는 주문번호도 같은 문구로 응답한다 — 존재 여부를 숨긴다")
    void 없는_주문도_같은_문구다() {
        String result = tools.orderStatus("00000", new ToolContext(Map.of("userId", "user1")));
        assertThat(result).isEqualTo("해당 주문을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("toolContext 에 userId 가 없으면 즉시 실패한다")
    void 컨텍스트가_없으면_실패한다() {
        assertThrows(IllegalStateException.class,
                () -> tools.orderStatus("12345", new ToolContext(Map.of())));
    }
}
