package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObligationApplicationServiceTest {

    private InMemoryMemberRepository members;
    private InMemoryObligationRepository obligations;
    private ObligationApplicationService service;
    private final LocalDate tradeDate = LocalDate.of(2026, 9, 18);
    private final LocalDate settleDate = LocalDate.of(2026, 9, 19);

    @BeforeEach
    void setUp() {
        members = new InMemoryMemberRepository();
        obligations = new InMemoryObligationRepository();
        service = new ObligationApplicationService(obligations, members);
        members.save(new Member("A", "Alpha Bank", MemberStatus.ACTIVE));
        members.save(new Member("B", "Beta Securities", MemberStatus.ACTIVE));
        members.save(new Member("S", "Suspended House", MemberStatus.SUSPENDED));
    }

    @Test
    void createsObligationWhenBothPartiesActive() {
        TradeObligation saved = service.create("A", "B", "USD", new BigDecimal("100"), tradeDate, settleDate);

        assertEquals("A", saved.getPayerMemberId());
        assertEquals("B", saved.getPayeeMemberId());
        assertEquals(ObligationStatus.OPEN, saved.getStatus());
        assertEquals(1, obligations.findAll().size());
    }

    @Test
    void rejectsMissingPayee() {
        DomainException ex = assertThrows(DomainException.class, () ->
                service.create("A", "GHOST", "USD", new BigDecimal("100"), tradeDate, settleDate));

        assertEquals("MEMBER_NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("payee"), "message should name the payee side");
        assertTrue(obligations.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    @Test
    void rejectsSuspendedPayee() {
        DomainException ex = assertThrows(DomainException.class, () ->
                service.create("A", "S", "USD", new BigDecimal("100"), tradeDate, settleDate));

        assertEquals("SUSPENDED_MEMBER", ex.getCode());
        assertTrue(ex.getMessage().contains("payee"), "message should name the payee side");
        assertTrue(obligations.findAll().isEmpty(), "suspended payee must not be saved");
    }

    @Test
    void rejectsSuspendedPayer() {
        DomainException ex = assertThrows(DomainException.class, () ->
                service.create("S", "A", "USD", new BigDecimal("100"), tradeDate, settleDate));

        assertEquals("SUSPENDED_MEMBER", ex.getCode());
        assertTrue(ex.getMessage().contains("payer"), "message should name the payer side");
        assertTrue(obligations.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    @Test
    void rejectsMissingPayer() {
        DomainException ex = assertThrows(DomainException.class, () ->
                service.create("GHOST", "A", "USD", new BigDecimal("100"), tradeDate, settleDate));

        assertEquals("MEMBER_NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("payer"), "message should name the payer side");
        assertTrue(obligations.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    // --- in-memory test doubles ---

    static class InMemoryMemberRepository implements MemberRepositoryPort {
        private final Map<String, Member> store = new HashMap<>();

        @Override
        public Member save(Member member) {
            store.put(member.getMemberId(), member);
            return member;
        }

        @Override
        public Optional<Member> findById(String memberId) {
            return Optional.ofNullable(store.get(memberId));
        }

        @Override
        public List<Member> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<Member> findByIds(Iterable<String> memberIds) {
            List<Member> out = new ArrayList<>();
            memberIds.forEach(id -> findById(id).ifPresent(out::add));
            return out;
        }
    }

    static class InMemoryObligationRepository implements ObligationRepositoryPort {
        private final List<TradeObligation> store = new ArrayList<>();

        @Override
        public TradeObligation save(TradeObligation obligation) {
            store.add(obligation);
            return obligation;
        }

        @Override
        public List<TradeObligation> saveAll(List<TradeObligation> batch) {
            store.addAll(batch);
            return batch;
        }

        @Override
        public Optional<TradeObligation> findById(String obligationId) {
            return store.stream().filter(o -> o.getObligationId().equals(obligationId)).findFirst();
        }

        @Override
        public List<TradeObligation> findAll() {
            return List.copyOf(store);
        }

        @Override
        public List<TradeObligation> findByFilters(String currency, LocalDate settleDate, ObligationStatus status) {
            return store.stream()
                    .filter(o -> currency == null || o.getCurrency().equals(currency))
                    .filter(o -> settleDate == null || o.getSettleDate().equals(settleDate))
                    .filter(o -> status == null || o.getStatus() == status)
                    .toList();
        }

        @Override
        public List<TradeObligation> findOpenBySettleDateAndCurrency(LocalDate date, String currency) {
            return store.stream()
                    .filter(o -> o.getStatus() == ObligationStatus.OPEN
                            && o.getSettleDate().equals(date)
                            && o.getCurrency().equals(currency))
                    .toList();
        }

        @Override
        public List<TradeObligation> findByNettingRunId(String runId) {
            return store.stream().filter(o -> runId.equals(o.getNettingRunId())).toList();
        }
    }
}
