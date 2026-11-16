package com.jinbooks.persistence.service.impl;

import com.jinbooks.entity.Message;
import com.jinbooks.entity.book.Book;
import com.jinbooks.entity.voucher.Voucher;
import com.jinbooks.entity.voucher.VoucherAuxiliary;
import com.jinbooks.entity.voucher.VoucherItem;
import com.jinbooks.entity.voucher.dto.VoucherChangeDto;
import com.jinbooks.entity.voucher.dto.VoucherItemChangeDto;
import com.jinbooks.entity.voucher.vo.VoucherItemVo;
import com.jinbooks.entity.voucher.vo.VoucherVo;
import com.jinbooks.exception.BusinessException;
import com.jinbooks.persistence.mapper.*;
import com.jinbooks.persistence.service.ConfigSysService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

public class VoucherFinancialRegressionTest {
    @Test
    public void rejectsOneCentImbalance() {
        reject(amounts("100.00", "99.99"));
    }

    @Test
    public void rejectsZeroAndDoubleSidedEntries() {
        reject(List.of(item("0", "0")));
        reject(List.of(item("100", "100")));
    }

    @Test
    public void rejectsFractionalCents() {
        reject(amounts("0.001", "0.001"));
    }

    @Test
    public void acceptsExactDecimalTotalsAndRedEntries() {
        assertEquals(Message.SUCCESS, submit(List.of(item("0.10", null), item("0.20", null), item(null, "0.30"))).getCode());
        assertEquals(Message.SUCCESS, submit(amounts("-100.00", "-100.00")).getCode());
    }

    @Test
    public void deletionReversesBalancesBeforeRemovingDetails() {
        State state = new State();
        Message<String> result = new Probe(state).delete(List.of("voucher-1"), "book-1");
        assertEquals(Message.SUCCESS, result.getCode());
        assertEquals(new BigDecimal("100.00"), state.reversed);
        assertTrue(state.items.isEmpty());
    }

    @Test
    public void cancellationKeepsDetailsForReviewHistory() {
        State state = new State();
        state.voucher.setStatus("reviewing");
        new Probe(state).cancelByIds(List.of("voucher-1"), "book-1");
        assertEquals(1, state.items.size());
        assertEquals("cancelled", state.voucher.getStatus());
    }

    private static void reject(List<VoucherItemChangeDto> items) {
        try {
            submit(items);
            fail("Invalid voucher was submitted");
        } catch (BusinessException expected) {
            assertEquals(Integer.valueOf(Message.FAIL), expected.getCode());
        }
    }

    private static Message<String> submit(List<VoucherItemChangeDto> items) {
        VoucherChangeDto dto = new VoucherChangeDto();
        dto.setBookId("book-1");
        dto.setVoucherDate(Date.from(Instant.parse("2026-11-16T00:00:00Z")));
        dto.setItems(items);
        return new Probe(new State()).submit(dto, false);
    }

    private static List<VoucherItemChangeDto> amounts(String debit, String credit) {
        return List.of(item(debit, null), item(null, credit));
    }

    private static VoucherItemChangeDto item(String debit, String credit) {
        VoucherItemChangeDto item = new VoucherItemChangeDto();
        item.setSubjectId("subject-1");
        item.setDebitAmount(debit == null ? null : new BigDecimal(debit));
        item.setCreditAmount(credit == null ? null : new BigDecimal(credit));
        return item;
    }

    private static class State {
        final Voucher voucher = Voucher.builder().id("voucher-1").bookId("book-1").status("completed").carryForward("n").build();
        final List<VoucherItem> items = new ArrayList<>(List.of(VoucherItem.builder().id("item-1").voucherId("voucher-1")
                .bookId("book-1").debitAmount(new BigDecimal("100.00")).build()));
        BigDecimal reversed = BigDecimal.ZERO;

        BookMapper books() {
            Book book = new Book();
            book.setId("book-1");
            book.setVoucherReviewed(0);
            return proxy(BookMapper.class, (name, args) -> book);
        }

        VoucherMapper vouchers() {
            return proxy(VoucherMapper.class, (name, args) -> switch (name) {
                case "selectList" -> new ArrayList<>(List.of(voucher));
                case "delete" -> 1;
                case "updateBatchById" -> true;
                default -> throw new UnsupportedOperationException(name);
            });
        }

        VoucherItemMapper details() {
            return proxy(VoucherItemMapper.class, (name, args) -> {
                if (name.equals("selectList")) return new ArrayList<>(items);
                if (name.equals("delete")) {
                    items.clear();
                    return 1;
                }
                throw new UnsupportedOperationException(name);
            });
        }
    }

    private static class Probe extends VoucherServiceImpl {
        final State state;

        Probe(State state) {
            super(null, state.details(), null,
                    proxy(VoucherItemAuxiliaryMapper.class, (name, args) -> name.equals("selectList") ? List.of() : 1),
                    null, proxy(com.jinbooks.persistence.service.BookSubjectService.class, (name, args) -> {
                        var subject = new com.jinbooks.entity.book.BookSubject();
                        subject.setId("subject-1");
                        subject.setBookId("book-1");
                        return subject;
                    }), null, state.books(),
                    proxy(ConfigSysService.class, (name, args) -> "2026-11"), null,
                    proxy(VoucherItemCashFlowMapper.class, (name, args) -> 1), null, null, null, null, null);
            this.state = state;
            this.baseMapper = state.vouchers();
        }

        @Override
        public Message<String> update(VoucherChangeDto dto) {
            return Message.ok("voucher-1");
        }

        @Override
        public Message<VoucherVo> queryById(String id) {
            VoucherVo voucher = new VoucherVo();
            voucher.setId(id);
            voucher.setCarryForward("n");
            List<VoucherItemVo> items = new ArrayList<>();
            for (VoucherItem item : state.items) {
                VoucherItemVo view = new VoucherItemVo();
                view.setDebitAmount(item.getDebitAmount());
                items.add(view);
            }
            voucher.setItems(items);
            return Message.ok(voucher);
        }

        @Override
        public void updateSubjectBalance(List<VoucherItem> items, List<VoucherAuxiliary> auxiliaries, boolean cancel) {
            assertTrue(cancel);
            for (VoucherItem item : items) state.reversed = state.reversed.add(item.getDebitAmount());
        }
    }

    private interface Call {
        Object apply(String name, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> call.apply(method.getName(), args));
    }
}
