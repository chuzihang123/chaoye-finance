package com.jinbooks.persistence.service.impl;

import com.jinbooks.entity.Message;
import com.jinbooks.entity.book.Book;
import com.jinbooks.entity.book.Settlement;
import com.jinbooks.persistence.mapper.BookMapper;
import com.jinbooks.persistence.mapper.VoucherItemMapper;
import com.jinbooks.persistence.mapper.VoucherMapper;
import com.jinbooks.persistence.service.ConfigSysService;
import com.jinbooks.persistence.service.VoucherService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SettlementGuardRegressionTest {
    @Test
    public void pendingVouchersFailVerificationAndDirectCheckout() {
        SettlementServiceImpl service = service(1, 0);
        assertEquals(Message.FAIL, service.verify("book-1").getCode());
        assertEquals(Message.FAIL, service.checkout(request("2026-11")).getCode());
    }

    @Test
    public void individuallyUnbalancedVoucherFailsVerification() {
        assertEquals(Message.FAIL, service(0, 1).verify("book-1").getCode());
    }

    @Test
    public void balancedCompletedVouchersPassVerification() {
        assertEquals(Message.SUCCESS, service(0, 0).verify("book-1").getCode());
    }

    @Test
    public void missingOrStalePeriodCannotAdvanceBook() {
        SettlementServiceImpl service = service(0, 0);
        assertEquals(Message.FAIL, service.checkout(request(null)).getCode());
        assertEquals(Message.FAIL, service.checkout(request("2026-10")).getCode());
    }

    private Settlement request(String period) {
        Settlement request = new Settlement();
        request.setBookId("book-1");
        request.setCurrentTerm(period);
        return request;
    }

    private SettlementServiceImpl service(long pending, long unbalanced) {
        SettlementServiceImpl service = new SettlementServiceImpl();
        service.bookMapper = proxy(BookMapper.class, (name, args) -> new Book());
        service.configSysService = proxy(ConfigSysService.class, (name, args) -> "2026-11");
        service.voucherService = proxy(VoucherService.class, (name, args) -> Message.ok(List.of()));
        service.voucherItemMapper = proxy(VoucherItemMapper.class, (name, args) -> List.of());
        AtomicInteger countCalls = new AtomicInteger();
        service.voucherMapper = proxy(VoucherMapper.class, (name, args) ->
                countCalls.getAndIncrement() % 2 == 0 ? pending : unbalanced);
        return service;
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
