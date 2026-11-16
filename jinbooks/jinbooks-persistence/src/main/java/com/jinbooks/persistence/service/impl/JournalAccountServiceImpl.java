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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinbooks.entity.Message;
import com.jinbooks.entity.dto.ListIdsDto;
import com.jinbooks.entity.journal.JournalAccount;
import com.jinbooks.entity.journal.dto.JournalAccountDto;
import com.jinbooks.entity.journal.dto.JournalAccountPageDto;
import com.jinbooks.persistence.mapper.JournalAccountMapper;
import com.jinbooks.persistence.service.JournalAccountService;
import com.jinbooks.persistence.mapper.BookMapper;
import com.jinbooks.persistence.mapper.JournalEntryMapper;
import com.jinbooks.entity.journal.JournalEntry;
import com.jinbooks.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class JournalAccountServiceImpl extends ServiceImpl<JournalAccountMapper, JournalAccount> implements JournalAccountService {
    @Autowired
    BookMapper bookMapper;
    @Autowired
    JournalEntryMapper journalEntryMapper;


    @Override
    public Message<Page<JournalAccount>> pageList(JournalAccountPageDto dto) {
        Page<JournalAccount> page = this.getBaseMapper().pageList(dto.build(), dto);
        return new Message<>(Message.SUCCESS, page);
    }
    
    @Override
    public Message<List <JournalAccount> > findAll(String bookId) {
    	LambdaQueryWrapper<JournalAccount> wrapper = new LambdaQueryWrapper<>();
    	wrapper.eq(JournalAccount::getBookId, bookId);
        return new Message<>(Message.SUCCESS, this.getBaseMapper().selectList(wrapper));
    }

    @Override
    @Transactional
    public Message<String> save(JournalAccountDto dto) {
        validateAccount(dto);
        JournalAccount account = new JournalAccount();
        BeanUtil.copyProperties(dto, account);
        //期初余额仅新增时可设置, 开户余额即期初余额
        if (account.getOpeningBalance() != null) {
            account.setBalance(account.getOpeningBalance());
        } else {
            account.setOpeningBalance(null);
        }
        boolean saveResult = super.save(account);
        return saveResult ? new Message<>(Message.SUCCESS, "新增成功") : new Message<>(Message.FAIL, "新增失败");
    }

    @Override
    @Transactional
    public Message<String> update(JournalAccountDto dto) {
        validateAccount(dto);
        String id = dto.getId();
        JournalAccount account = super.getById(id);
        if (account == null || !dto.getBookId().equals(account.getBookId())) {
            throw new BusinessException(403, "账户不存在或不属于当前账套");
        }
        BeanUtil.copyProperties(dto, account);
        //期初余额与余额不可通过编辑修改
        account.setOpeningBalance(null);
        account.setBalance(null);
        boolean result = super.updateById(account);
        return result ? new Message<>(Message.SUCCESS, "修改成功") : new Message<>(Message.FAIL, "修改失败");
    }

    @Override
    @Transactional
    public Message<String> delete(ListIdsDto dto) {
        List<String> listIds = dto.getListIds();
        if (bookMapper.selectForUpdate(dto.getBookId()) == null) throw new BusinessException(400, "账套不存在");
        if (super.count(new LambdaQueryWrapper<JournalAccount>().eq(JournalAccount::getBookId, dto.getBookId())
                .in(JournalAccount::getId, listIds)) != new HashSet<>(listIds).size()) {
            throw new BusinessException(403, "账户不存在或不属于当前账套");
        }
        if (journalEntryMapper.exists(new LambdaQueryWrapper<JournalEntry>().eq(JournalEntry::getBookId, dto.getBookId())
                .in(JournalEntry::getAccId, listIds))) throw new BusinessException(400, "账户已有流水，不能删除");
        //删除
        boolean result = super.removeByIds(listIds);

        return result ? new Message<>(Message.SUCCESS, "删除成功") : new Message<>(Message.FAIL, "删除失败");
    }

    private void validateAccount(JournalAccountDto dto) {
        if (StringUtils.isBlank(dto.getAccCode()) || StringUtils.isBlank(dto.getAccName())) {
            throw new BusinessException(400, "账户编码和名称不能为空");
        }
        if (bookMapper.selectForUpdate(dto.getBookId()) == null) throw new BusinessException(400, "账套不存在");
        if (baseMapper.exists(new LambdaQueryWrapper<JournalAccount>().eq(JournalAccount::getBookId, dto.getBookId())
                .eq(JournalAccount::getAccCode, dto.getAccCode())
                .ne(StringUtils.isNotBlank(dto.getId()), JournalAccount::getId, dto.getId()))) {
            throw new BusinessException(400, "本账套账户编码重复");
        }
    }

	@Override
	public int income(String accId, BigDecimal income) {
		return this.getBaseMapper().income(accId, income);
	}

	@Override
	public int expenditure(String accId, BigDecimal expenditure) {
		return this.getBaseMapper().expenditure(accId, expenditure);
	}

	/**
	 * 结账：本期期初余额=余额
	 */
	@Override
	public int checkout(String bookId) {
		return this.getBaseMapper().checkout(bookId);
	}
}
