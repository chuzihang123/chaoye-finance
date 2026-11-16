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
 

package com.jinbooks.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinbooks.entity.book.SettlementCarryforward;
import com.jinbooks.entity.book.vo.SettlementCarryforwardVo;
import com.jinbooks.entity.voucher.dto.VoucherTemplatePageDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SettlementCarryforwardMapper extends BaseMapper<SettlementCarryforward> {
    @Select("""
        SELECT carry.* FROM jbx_settlement_carryforward carry
        JOIN jbx_voucher_template template ON template.id=carry.voucher_template_id
        WHERE carry.book_id=#{bookId} AND carry.year_period=#{period} AND carry.deleted='n'
          AND template.code=#{code} AND template.category=#{category}
        ORDER BY carry.created_date,carry.id LIMIT 1
        """)
    SettlementCarryforward findByTemplateCode(@Param("bookId") String bookId, @Param("period") String period,
            @Param("code") String code, @Param("category") Integer category);
    Page<SettlementCarryforwardVo> pageList(Page page, @Param("dto") VoucherTemplatePageDto dto);
}
