package com.skala.helpdesk.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

/**
 * 승인 게이트의 저장소 — 도구는 접수(PENDING)만 만들 수 있고,
 * 승인(APPROVED)은 사람이 REST API 로만 바꾼다. 승인 API 는 도구 목록에 없으므로 모델이 부를 수 없다.
 */
@Repository
public class TicketRepository {

    public enum Status { PENDING, APPROVED }

    public record Ticket(String no, String orderId, String type, String reason,
                         String userId, Status status, Instant requestedAt) {}

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(0);

    /** 접수만 — 상태는 언제나 PENDING 으로 시작한다. */
    public Ticket request(String orderId, String type, String reason, String userId) {
        Ticket t = new Ticket("T-%04d".formatted(seq.incrementAndGet()),
                orderId, type, reason, userId, Status.PENDING, Instant.now());
        tickets.put(t.no(), t);
        return t;
    }

    public List<Ticket> pending() {
        return tickets.values().stream()
                .filter(t -> t.status() == Status.PENDING)
                .sorted((a, b) -> a.no().compareTo(b.no()))
                .toList();
    }

    public List<Ticket> findByUser(String userId) {
        return tickets.values().stream()
                .filter(t -> t.userId().equals(userId))
                .sorted((a, b) -> a.no().compareTo(b.no()))
                .toList();
    }

    /** 사람 전용 — 승인. 도구에서는 절대 호출하지 않는다. */
    public Optional<Ticket> approve(String no) {
        return Optional.ofNullable(tickets.computeIfPresent(no,
                (k, t) -> new Ticket(t.no(), t.orderId(), t.type(), t.reason(),
                        t.userId(), Status.APPROVED, t.requestedAt())));
    }
}
