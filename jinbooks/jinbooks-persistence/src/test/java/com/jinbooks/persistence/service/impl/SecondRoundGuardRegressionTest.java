package com.jinbooks.persistence.service.impl;

import com.jinbooks.entity.config.dto.ConfigPersonalTaxChangeDto;
import com.jinbooks.entity.standard.StandardStatementBalanceSheet;
import com.jinbooks.entity.standard.StandardStatementIncome;
import com.jinbooks.entity.statement.StatementRules;
import com.jinbooks.entity.statement.StatementSubjectBalance;
import com.jinbooks.entity.voucher.dto.VoucherTemplateChangeDto;
import com.jinbooks.exception.BusinessException;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.Assert.*;

public class SecondRoundGuardRegressionTest {
    @Test(expected = BusinessException.class)
    public void balanceTemplateRejectsInvalidSymbolBeforeWriting() {
        StandardStatementBalanceSheet row = new StandardStatementBalanceSheet();
        row.setSymbol("?");
        new StandardStatementBalanceSheetServiceImpl(null, null, null).save(row);
    }

    @Test(expected = BusinessException.class)
    public void incomeTemplateRejectsInvalidSymbolBeforeWriting() {
        StandardStatementIncome row = new StandardStatementIncome();
        row.setSymbol("?");
        new StandardStatementIncomeServiceImpl(null, null, null).save(row);
    }

    @Test(expected = BusinessException.class)
    public void emptyVoucherTemplateCannotReachPersistence() {
        VoucherTemplateChangeDto dto = new VoucherTemplateChangeDto();
        dto.setName("");
        dto.setItems(Collections.emptyList());
        new VoucherTemplateServiceImpl().save(dto);
    }

    @Test(expected = BusinessException.class)
    public void taxAboveOneHundredIsRejectedBeforeDatabase() {
        ConfigPersonalTaxChangeDto dto = new ConfigPersonalTaxChangeDto();
        dto.setLevel(1);
        dto.setMinNum(0);
        dto.setMaxNum(100);
        dto.setTaxRate(101);
        new ConfigPersonalTaxServiceImpl().save(dto);
    }

    @Test(expected = BusinessException.class)
    public void negativeTaxIsRejectedBeforeDatabase() {
        ConfigPersonalTaxChangeDto dto = new ConfigPersonalTaxChangeDto();
        dto.setTaxRate(-1);
        new ConfigPersonalTaxServiceImpl().save(dto);
    }

    @Test
    public void creditNormalBalanceIsPositiveAndDoesNotAccumulateOnRefresh() {
        StatementBalanceSheetServiceImpl service = new StatementBalanceSheetServiceImpl(
                null, null, null, null, null, null, null, null, null, null);
        StatementSubjectBalance balance = new StatementSubjectBalance();
        balance.setDirection("2");
        balance.setOpeningYearBalanceDebit(BigDecimal.ZERO);
        balance.setOpeningYearBalanceCredit(new BigDecimal("10000"));
        balance.setClosingBalanceDebit(BigDecimal.ZERO);
        balance.setClosingBalanceCredit(new BigDecimal("10900"));
        balance.setBalance(new BigDecimal("-10900"));
        StatementRules rule = new StatementRules();
        rule.setRule("BALANCE");
        service.updateRuleBalance(balance, rule);
        service.updateRuleBalance(balance, rule);
        assertEquals(0, rule.getOpeningYearBalance().compareTo(new BigDecimal("10000")));
        assertEquals(0, rule.getClosingBalance().compareTo(new BigDecimal("10900")));
    }
}
