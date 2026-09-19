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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObligationApplicationServiceTest {

    private InMemoryMemberRepository memberRepository;
    private InMemoryObligationRepository obligationRepository;
    private ObligationApplicationService service;
    private LocalDate tradeDate;
    private LocalDate settleDate;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        obligationRepository = new InMemoryObligationRepository();
        service = new ObligationApplicationService(obligationRepository, memberRepository);
        tradeDate = LocalDate.of(2026, 9, 18);
        settleDate = LocalDate.of(2026, 9, 19);
        memberRepository.save(new Member("PAYER", "Payer Bank", MemberStatus.ACTIVE));
        memberRepository.save(new Member("PAYEE", "Payee Bank", MemberStatus.ACTIVE));
        memberRepository.save(new Member("SUSPENDED", "Suspended Bank", MemberStatus.SUSPENDED));
    }

    @Test
    void createsObligationWhenBothPartiesActive() {
        TradeObligation saved = service.create(
                "PAYER", "PAYEE", "USD", new BigDecimal("1000"), tradeDate, settleDate);

        assertEquals(ObligationStatus.OPEN, saved.getStatus());
        assertEquals(1, obligationRepository.findAll().size());
        List<TradeObligation> listed = service.list("USD", settleDate, ObligationStatus.OPEN);
        assertEquals(1, listed.size());
        assertEquals(saved.getObligationId(), listed.get(0).getObligationId());
    }

    @Test
    void rejectsUnknownPayee() {
        DomainException ex = assertThrows(DomainException.class, () -> service.create(
                "PAYER", "NO_SUCH_MEMBER", "USD", new BigDecimal("1000"), tradeDate, settleDate));

        assertEquals("MEMBER_NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("payee"));
        assertTrue(ex.getMessage().contains("NO_SUCH_MEMBER"));
        assertTrue(obligationRepository.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    @Test
    void rejectsSuspendedPayee() {
        DomainException ex = assertThrows(DomainException.class, () -> service.create(
                "PAYER", "SUSPENDED", "USD", new BigDecimal("1000"), tradeDate, settleDate));

        assertEquals("SUSPENDED_MEMBER", ex.getCode());
        assertTrue(ex.getMessage().contains("payee"));
        assertTrue(ex.getMessage().contains("SUSPENDED"));
        assertTrue(obligationRepository.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    @Test
    void rejectsUnknownPayer() {
        DomainException ex = assertThrows(DomainException.class, () -> service.create(
                "NO_SUCH_MEMBER", "PAYEE", "USD", new BigDecimal("1000"), tradeDate, settleDate));

        assertEquals("MEMBER_NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("payer"));
        assertTrue(obligationRepository.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    @Test
    void rejectsSuspendedPayer() {
        DomainException ex = assertThrows(DomainException.class, () -> service.create(
                "SUSPENDED", "PAYEE", "USD", new BigDecimal("1000"), tradeDate, settleDate));

        assertEquals("SUSPENDED_MEMBER", ex.getCode());
        assertTrue(ex.getMessage().contains("payer"));
        assertTrue(obligationRepository.findAll().isEmpty(), "rejected create must not leave dirty rows");
    }

    private static final class InMemoryMemberRepository implements MemberRepositoryPort {
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
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Member> findByIds(Iterable<String> memberIds) {
            List<Member> result = new ArrayList<>();
            for (String id : memberIds) {
                Member member = store.get(id);
                if (member != null) {
                    result.add(member);
                }
            }
            return result;
        }
    }

    private static final class InMemoryObligationRepository implements ObligationRepositoryPort {
        private final Map<String, TradeObligation> store = new HashMap<>();

        @Override
        public TradeObligation save(TradeObligation obligation) {
            store.put(obligation.getObligationId(), obligation);
            return obligation;
        }

        @Override
        public List<TradeObligation> saveAll(List<TradeObligation> obligations) {
            obligations.forEach(this::save);
            return obligations;
        }

        @Override
        public Optional<TradeObligation> findById(String obligationId) {
            return Optional.ofNullable(store.get(obligationId));
        }

        @Override
        public List<TradeObligation> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<TradeObligation> findByFilters(String currency, LocalDate settleDate, ObligationStatus status) {
            return store.values().stream()
                    .filter(o -> currency == null || o.getCurrency().equalsIgnoreCase(currency))
                    .filter(o -> settleDate == null || o.getSettleDate().equals(settleDate))
                    .filter(o -> status == null || o.getStatus() == status)
                    .collect(Collectors.toList());
        }

        @Override
        public List<TradeObligation> findOpenBySettleDateAndCurrency(LocalDate settleDate, String currency) {
            return findByFilters(currency, settleDate, ObligationStatus.OPEN);
        }

        @Override
        public List<TradeObligation> findByNettingRunId(String runId) {
            return store.values().stream()
                    .filter(o -> runId.equals(o.getNettingRunId()))
                    .collect(Collectors.toList());
        }
    }
}
