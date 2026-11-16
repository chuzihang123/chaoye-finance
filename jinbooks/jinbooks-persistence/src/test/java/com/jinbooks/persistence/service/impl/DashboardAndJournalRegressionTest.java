package com.jinbooks.persistence.service.impl;

import com.jinbooks.entity.journal.dto.JournalAccountDto;
import com.jinbooks.entity.statement.StatementSubjectBalance;
import com.jinbooks.entity.statement.dto.StatementParamsDto;
import com.jinbooks.exception.BusinessException;
import com.jinbooks.persistence.mapper.StatementSubjectBalanceMapper;
import com.jinbooks.persistence.service.ConfigSysService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

public class DashboardAndJournalRegressionTest {
    @Test
    public void cashIncomeAndPayoutUseSeparateAccumulators() {
        ConfigSysService config = (ConfigSysService) Proxy.newProxyInstance(ConfigSysService.class.getClassLoader(),
                new Class<?>[]{ConfigSysService.class}, (proxy, method, args) -> "1001,1002");
        StatementSubjectBalanceMapper mapper = (StatementSubjectBalanceMapper) Proxy.newProxyInstance(
                StatementSubjectBalanceMapper.class.getClassLoader(), new Class<?>[]{StatementSubjectBalanceMapper.class},
                (proxy, method, args) -> List.of(balance("1001", "0", "0", "0"), balance("1002", "10900", "1000", "100")));
        FundDashboardServiceImpl service = new FundDashboardServiceImpl(config, mapper, null, null);
        StatementParamsDto dto = new StatementParamsDto();
        dto.setBookId("book-a");
        dto.setDateRange(new String[]{"2026-11", "2026-11"});
        var result = service.statisticsFundBalance(dto);
        assertEquals(0, result.getIncomeFunds().compareTo(new BigDecimal("1000")));
        assertEquals(0, result.getPayoutFunds().compareTo(new BigDecimal("100")));
        assertEquals(0, result.getNetIncomeFunds().compareTo(new BigDecimal("900")));
    }

    @Test(expected = BusinessException.class)
    public void journalAccountNameIsRequiredBeforePersistence() {
        JournalAccountDto dto = new JournalAccountDto();
        dto.setAccCode("QA-1");
        new JournalAccountServiceImpl().save(dto);
    }

    private StatementSubjectBalance balance(String code, String amount, String debit, String credit) {
        StatementSubjectBalance row = new StatementSubjectBalance();
        row.setSubjectCode(code);
        row.setBalance(new BigDecimal(amount));
        row.setCurrentPeriodDebit(new BigDecimal(debit));
        row.setCurrentPeriodCredit(new BigDecimal(credit));
        return row;
    }
}
