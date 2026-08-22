package com.skala.helpdesk.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * Phase 4 — 데이터 접근. 소유자 검증은 조회 조건 자체에 넣는다(findOwned).
 * findById 후 자바에서 비교하는 방식은 실수 한 번에 남의 주문이 새어 나간다.
 *
 * 시드(교재 Day1 과 동일한 검증 데이터): 12345·12346·12347=user1, 99999=user2
 */
@Repository
public class OrderRepository {

    public enum Status {
        PAID("결제완료"), PREPARING("상품 준비중"), SHIPPING("배송중"), DELIVERED("배송완료");

        private final String label;
        Status(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Order(String id, String userId, String item, Status status, LocalDate eta) {}

    private final List<Order> orders = List.of(
            // 12345: 3일 전 배송완료 — "그럼 그거 반품 돼요?" 시나리오의 주인공 (7일 이내 → 가능)
            new Order("12345", "user1", "무선 이어폰", Status.DELIVERED, LocalDate.now().minusDays(3)),
            // 12346: 배송중 — 규정 대조 시나리오 (배송중에는 교환·환불 접수 불가)
            new Order("12346", "user1", "러닝화 265mm", Status.SHIPPING, LocalDate.now().plusDays(1)),
            new Order("12347", "user1", "블루투스 키보드", Status.PREPARING, LocalDate.now().plusDays(3)),
            // 99999: 남의 주문 — 권한 격리 검증용
            new Order("99999", "user2", "게이밍 모니터", Status.DELIVERED, LocalDate.now().minusDays(1)));

    /** 권한은 쿼리 조건에 — 주문번호와 소유자가 모두 일치해야만 반환한다. */
    public Optional<Order> findOwned(String orderId, String userId) {
        return orders.stream()
                .filter(o -> o.id().equals(orderId) && o.userId().equals(userId))
                .findFirst();
    }
}
