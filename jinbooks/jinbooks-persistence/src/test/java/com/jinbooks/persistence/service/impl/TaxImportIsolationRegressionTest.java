package com.jinbooks.persistence.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinbooks.entity.ExcelImport;
import com.jinbooks.entity.book.Book;
import com.jinbooks.entity.hr.EmployeeTaxDeduction;
import com.jinbooks.entity.idm.UserInfo;
import com.jinbooks.exception.BusinessException;
import com.jinbooks.persistence.mapper.BookMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class TaxImportIsolationRegressionTest {
    @BeforeClass
    public static void mapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), EmployeeTaxDeduction.class);
    }

    @Test
    public void importDeletesOnlyTheCurrentBooksPreviousRows() {
        Probe service = new Probe();
        service.importFromExcel(input(false), user());
        assertTrue(service.removed.getSqlSegment().contains("book_id"));
        assertTrue(service.removed.getParamNameValuePairs().containsValue("book-a"));
        assertEquals("book-a", service.saved.get(0).getBookId());
        assertEquals(Double.valueOf(321), service.saved.get(0).getRent());
    }

    @Test
    public void invalidRowReportsLocationBeforeAnyDelete() {
        Probe service = new Probe();
        try {
            service.importFromExcel(input(true), user());
            fail("Invalid amount was accepted");
        } catch (BusinessException expected) {
            assertTrue(expected.getMessage().contains("第2行"));
            assertNull(service.removed);
            assertNull(service.saved);
        }
    }

    private UserInfo user() {
        UserInfo user = new UserInfo();
        user.setBookId("book-a");
        return user;
    }

    private ExcelImport input(boolean invalid) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("扣除资料");
        sheet.createRow(0);
        var row = sheet.createRow(1);
        row.createCell(0).setCellValue("QA-1");
        row.createCell(1).setCellValue("QA员工");
        row.createCell(3).setCellValue("QA-ID-1");
        for (int column = 12; column < 22; column++) row.createCell(column).setCellValue(0);
        if (invalid) row.getCell(15).setCellValue("错误金额");
        else row.getCell(15).setCellValue(321);
        return new ExcelImport() {
            @Override public boolean isExcelNotEmpty() { return true; }
            @Override public Workbook biuldWorkbook() { return workbook; }
            @Override public void closeWorkbook() {
                try { workbook.close(); } catch (java.io.IOException error) { throw new AssertionError(error); }
            }
        };
    }

    private static class Probe extends EmployeeTaxDeductionServiceImpl {
        LambdaQueryWrapper<EmployeeTaxDeduction> removed;
        List<EmployeeTaxDeduction> saved;
        Probe() {
            bookMapper = (BookMapper) Proxy.newProxyInstance(BookMapper.class.getClassLoader(), new Class<?>[]{BookMapper.class},
                    (proxy, method, args) -> new Book());
        }
        @Override public boolean remove(Wrapper<EmployeeTaxDeduction> wrapper) {
            removed = (LambdaQueryWrapper<EmployeeTaxDeduction>) wrapper;
            return true;
        }
        @Override public boolean saveBatch(Collection<EmployeeTaxDeduction> values) {
            saved = List.copyOf(values);
            return true;
        }
    }
}
