/*
 * Copyright [2025] [JinBooks of copyright http://www.jinbooks.com]
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
 

package com.jinbooks.persistence.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinbooks.entity.Message;
import com.jinbooks.entity.book.Book;
import com.jinbooks.entity.book.BookSubject;
import com.jinbooks.entity.dto.ListIdsDto;
import com.jinbooks.entity.journal.JournalAccount;
import com.jinbooks.entity.journal.JournalEntry;
import com.jinbooks.entity.journal.dto.JournalEntryDto;
import com.jinbooks.entity.journal.dto.JournalEntryPageDto;
import com.jinbooks.entity.voucher.dto.GenerateVoucherDto;
import com.jinbooks.entity.voucher.dto.VoucherChangeDto;
import com.jinbooks.entity.voucher.dto.VoucherItemChangeDto;
import com.jinbooks.enums.VoucherStatusEnum;
import com.jinbooks.exception.BusinessException;
import com.jinbooks.persistence.mapper.BookMapper;
import com.jinbooks.persistence.mapper.JournalEntryMapper;
import com.jinbooks.persistence.service.BookSubjectService;
import com.jinbooks.persistence.service.JournalAccountService;
import com.jinbooks.persistence.service.JournalEntryService;
import com.jinbooks.persistence.service.SettlementService;
import com.jinbooks.persistence.service.VoucherService;
import com.jinbooks.util.DateUtils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class JournalEntryServiceImpl extends ServiceImpl<JournalEntryMapper, JournalEntry> implements JournalEntryService {

	@Autowired
	JournalAccountService journalAccountService;

	@Autowired
	BookSubjectService bookSubjectService;

	@Autowired
    BookMapper bookMapper;
	
	@Autowired
    VoucherService voucherService;
	
	@Autowired
	SettlementService settlementService;

    @Override
    public Message<Page<JournalEntry>> pageList(JournalEntryPageDto dto) {
        Page<JournalEntry> page = this.getBaseMapper().pageList(dto.build(), dto);

        return new Message<>(Message.SUCCESS, page);
    }

    @Override
    @Transactional
    public Message<String> save(JournalEntryDto dto) {

    	JournalEntry journalEntry = new JournalEntry();
        BeanUtil.copyProperties(dto, journalEntry);
        if(dto.getTradeDate() == null) {
        	journalEntry.setTradeDate(new Date());
        }
        //判断账期是否结账
        String period = DateUtils.format(journalEntry.getTradeDate(),DateUtils.FORMAT_DATE_YYYY_MM);
        Message<String> check = settlementService.check(dto.getBookId(), period);
        if(check.getCode() != 0) {
        	return check;
        }
        
        JournalAccount  journalAccount  = journalAccountService.getById(journalEntry.getAccId());
        
        if(journalEntry.getDirection().equalsIgnoreCase("i") 
        		|| journalEntry.getDirection().equalsIgnoreCase("o")) {
        	//i收入和o期初
        	journalEntry.setExpenditure(null);
        	if(journalEntry.getDirection().equalsIgnoreCase("o")&&journalAccount.getOpeningBalance().equals(BigDecimal.ZERO)) {
        		journalAccount.setOpeningBalance(journalEntry.getIncome());
        	}
        	journalAccountService.income(journalEntry.getAccId(), journalEntry.getIncome());
        	
        }else if(journalEntry.getDirection().equalsIgnoreCase("e")){
        	journalEntry.setIncome(null);
        	//支出
        	if(journalAccount.getBalance().subtract(journalEntry.getExpenditure()).doubleValue() < 0 ) {
        		throw new BusinessException(10001,"余额不足！");
        	}
        	journalAccountService.expenditure(journalEntry.getAccId(), journalEntry.getExpenditure());
        }
        
        if(journalAccount != null) {
        	JournalAccount journalAccountBalance  = journalAccountService.getById(journalEntry.getAccId());
        	//设置余额
        	journalEntry.setBalance(journalAccountBalance.getBalance());
        }
        boolean saveResult = super.save(journalEntry);

        return saveResult ? new Message<>(Message.SUCCESS, "新增成功") : new Message<>(Message.FAIL, "新增失败");
    }

    @Override
    @Transactional
    public Message<String> update(JournalEntryDto dto) {
        String id = dto.getId();
        JournalEntry oldJournalEntry = super.getById(id);
        //判断账期是否结账
        String period = DateUtils.format(oldJournalEntry.getTradeDate(),DateUtils.FORMAT_DATE_YYYY_MM);
        Message<String> check = settlementService.check(dto.getBookId(), period);
        if(check.getCode() != 0) {
        	return check;
        }
        
        // 先冲销原来的金额（反向操作）
        if(oldJournalEntry.getDirection().equalsIgnoreCase("i")
        		||oldJournalEntry.getDirection().equalsIgnoreCase("o")) {
        	// 原来的是收入/期初，现在要退回
        	journalAccountService.expenditure(oldJournalEntry.getAccId(), oldJournalEntry.getIncome());
        }else {
        	// 原来的是支出，现在要加回
        	journalAccountService.income(oldJournalEntry.getAccId(), oldJournalEntry.getExpenditure());
        }
        
        // 更新日记账数据
        BeanUtil.copyProperties(dto, oldJournalEntry);
        
        // 再应用新的金额（正向操作）
        JournalAccount journalAccount = journalAccountService.getById(oldJournalEntry.getAccId());
        
        if(oldJournalEntry.getDirection().equalsIgnoreCase("i") 
        		|| oldJournalEntry.getDirection().equalsIgnoreCase("o")) {
        	//i 收入和 o 期初
        	oldJournalEntry.setExpenditure(null);
        	if(oldJournalEntry.getDirection().equalsIgnoreCase("o")&&journalAccount.getOpeningBalance().equals(BigDecimal.ZERO)) {
        		journalAccount.setOpeningBalance(oldJournalEntry.getIncome());
        	}
        	journalAccountService.income(oldJournalEntry.getAccId(), oldJournalEntry.getIncome());
        	
        }else if(oldJournalEntry.getDirection().equalsIgnoreCase("e")){
        	oldJournalEntry.setIncome(null);
        	//支出
        	if(journalAccount.getBalance().subtract(oldJournalEntry.getExpenditure()).doubleValue() < 0 ) {
        		throw new BusinessException(10001,"余额不足！");
        	}
        	journalAccountService.expenditure(oldJournalEntry.getAccId(), oldJournalEntry.getExpenditure());
        }
        
        // 设置最新余额
        if(journalAccount != null) {
        	JournalAccount journalAccountBalance = journalAccountService.getById(oldJournalEntry.getAccId());
        	oldJournalEntry.setBalance(journalAccountBalance.getBalance());
        }
        
        boolean result = super.updateById(oldJournalEntry);
        return result ? new Message<>(Message.SUCCESS, "修改成功") : new Message<>(Message.FAIL, "修改失败");
    }

    @Override
    @Transactional
    public Message<String> delete(ListIdsDto dto) {
        List<String> listIds = dto.getListIds();
        List<String> removeableListIds = new ArrayList<>();
        List<String> voucherIds = new ArrayList<>();
        String bookId = null;
        boolean result = false;
        for(String id : listIds) {
	        JournalEntry journalEntry = super.getById(id);
	        if(journalEntry == null) {
	        	continue;
	        }
	        bookId = journalEntry.getBookId();
	        //判断账期是否结账，已结账账期不允许删除
	        if(journalEntry.getTradeDate() != null) {
		        String period = DateUtils.format(journalEntry.getTradeDate(),DateUtils.FORMAT_DATE_YYYY_MM);
		        Message<String> check = settlementService.check(bookId, period);
		        if(check.getCode() != 0) {
		        	return new Message<>(Message.FAIL, "日记账所在账期[" + period + "]已结账，不允许删除");
		        }
	        }
	        removeableListIds.add(id);
	        //已生成凭证的，同步删除凭证（已提交凭证按凭证管理删除流程处理）
	        if(StringUtils.isNotBlank(journalEntry.getVoucherId())) {
	        	voucherIds.add(journalEntry.getVoucherId());
	        }
	        if(journalEntry.getDirection().equalsIgnoreCase("i")
	        		||journalEntry.getDirection().equalsIgnoreCase("o")) {
	        	//支出退回
	        	journalAccountService.expenditure(journalEntry.getAccId(), journalEntry.getIncome());
	        }else {
	        	//收入减去
	        	journalAccountService.income(journalEntry.getAccId(), journalEntry.getExpenditure());
	        }
        }
        if(!voucherIds.isEmpty()) {
        	Message<String> voucherMsg = voucherService.delete(voucherIds, bookId);
        	if(voucherMsg.getCode() != Message.SUCCESS) {
        		return voucherMsg;
        	}
        }
        result = super.removeBatchByIds(removeableListIds);

        return result ? new Message<>(Message.SUCCESS, "删除成功") : new Message<>(Message.FAIL, "删除失败");
    }

	@Override
	public Message<String> generateVoucher(GenerateVoucherDto dto) {
		String bookId = dto.getBookId();
		Book book = bookMapper.selectById(bookId);
		JournalEntry journalEntry = super.getById(dto.getId());
		
	     // 使用日记账的交易日期作为凭证日期
        Date formattedDate = journalEntry.getTradeDate() != null ? journalEntry.getTradeDate() : DateUtils.getCurrentDate();

        //判断账期是否结账
        String period = DateUtils.format(formattedDate,DateUtils.FORMAT_DATE_YYYY_MM);
        Message<String> check = settlementService.check(bookId, period);
        if(check.getCode() != 0) {
        	return check;
        }
        
        BigDecimal amount = null;
		if(journalEntry.getDirection().equalsIgnoreCase("e")) {
			amount = journalEntry.getExpenditure();
		}else {
			amount = journalEntry.getIncome();
		}
		
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(formattedDate);
        Integer year = calendar.get(Calendar.YEAR);
        Integer month = calendar.get(Calendar.MONTH) + 1;

        // 封装凭证入参数据
        Integer wordNum = voucherService.getAbleWordNum(bookId,"记", null, null).getData();

        VoucherChangeDto voucherDto = new VoucherChangeDto();
        voucherDto.setWordHead("记");
        voucherDto.setWordNum(wordNum);
        voucherDto.setBookId(bookId);
        voucherDto.setCompanyName(book.getCompanyName());
        voucherDto.setVoucherDate(formattedDate);
        voucherDto.setVoucherYear(year);
        voucherDto.setVoucherMonth(month);
        voucherDto.setDebitAmount(amount);
        voucherDto.setCreditAmount(amount);
        
        // 账户的入账科目（现金/银行科目）
        JournalAccount journalAccount = journalAccountService.getById(journalEntry.getAccId());
        if (journalAccount == null || StringUtils.isBlank(journalAccount.getSubjectId())) {
        	return new Message<>(Message.FAIL, "生成凭证失败：账户未设置入账科目，请先在账户中设置");
        }
        BookSubject accSubject = bookSubjectService.getById(journalAccount.getSubjectId());
        if (accSubject == null) {
        	return new Message<>(Message.FAIL, "生成凭证失败：账户入账科目不存在");
        }
        // 日记账选择的对方科目
        if (StringUtils.isBlank(journalEntry.getSubjectId())) {
        	return new Message<>(Message.FAIL, "生成凭证失败：请先为日记账选择对方科目");
        }
        BookSubject counterSubject = bookSubjectService.getById(journalEntry.getSubjectId());
        if (counterSubject == null) {
        	return new Message<>(Message.FAIL, "生成凭证失败：对方科目不存在");
        }

	     // 创建凭证项
        List<VoucherItemChangeDto> voucherItems = new ArrayList<>();
        //
        VoucherItemChangeDto debitItemDto = null;
        VoucherItemChangeDto creditItemDto = null;
        if (journalEntry.getDirection().equalsIgnoreCase("e")) {
        	// 支出：借方记对方科目，贷方记现金/银行科目
        	debitItemDto = createJournalVoucherItem(journalEntry.getRemark(), counterSubject, amount, true);
        	creditItemDto = createJournalVoucherItem(journalEntry.getRemark(), accSubject, amount, false);
        } else {
        	// 收入/初始化：借方记现金/银行科目，贷方记对方科目
        	debitItemDto = createJournalVoucherItem(journalEntry.getRemark(), accSubject, amount, true);
        	creditItemDto = createJournalVoucherItem(journalEntry.getRemark(), counterSubject, amount, false);
        }
        voucherItems.add(debitItemDto);
        voucherItems.add(creditItemDto);

        voucherDto.setRemark(journalEntry.getRemark());
        // 暂存凭证
        voucherDto.setItems(voucherItems);
        //设置状态为暂存
        voucherDto.setStatus(VoucherStatusEnum.DRAFT.getValue());
        voucherService.save(voucherDto);
        
    	LambdaUpdateWrapper<JournalEntry> updateWrapper = new LambdaUpdateWrapper<>();
    	updateWrapper.set(JournalEntry::getVoucherId, voucherDto.getId());
    	updateWrapper.eq(JournalEntry::getId, dto.getId());
    	super.update(updateWrapper);
    	
		return Message.ok(voucherDto.getId());
	}

	private VoucherItemChangeDto createJournalVoucherItem(String summary, BookSubject subject, BigDecimal amount, boolean debit) {
		VoucherItemChangeDto itemDto = new VoucherItemChangeDto();
		itemDto.setSummary(summary);
		itemDto.setSubjectId(subject.getId());
		itemDto.setSubjectCode(subject.getCode());
		itemDto.setSubjectName(subject.getCode() + "-" + subject.getName());
		itemDto.setSubjectBalance(subject.getBalance());
		if (debit) {
			itemDto.setDebitAmount(amount);
		} else {
			itemDto.setCreditAmount(amount);
		}
		itemDto.setAuxiliary(List.of());
		itemDto.setDetailedAccounts("");
		return itemDto;
	}
}
