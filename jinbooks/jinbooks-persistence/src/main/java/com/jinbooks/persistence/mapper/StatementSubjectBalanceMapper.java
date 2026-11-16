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

import com.jinbooks.entity.statement.StatementSubjectBalance;
import com.jinbooks.entity.statement.dto.StatementParamsDto;
import com.jinbooks.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatementSubjectBalanceMapper extends BaseMapperPlus<StatementSubjectBalanceMapper, StatementSubjectBalance, StatementSubjectBalance> {
    @Select("""
        SELECT COALESCE(SUM(sb.closing_balance_credit-sb.closing_balance_debit),0) AS balance,
               COALESCE(SUM(sb.opening_year_balance_credit-sb.opening_year_balance_debit),0) AS opening_year_balance_credit
        FROM jbx_statement_subject_balance sb
        JOIN jbx_book_subject s ON s.book_id=sb.book_id AND s.code=sb.subject_code AND s.deleted='n'
        WHERE sb.book_id=#{bookId} AND sb.year_period=#{yearPeriod} AND sb.deleted='n'
          AND s.category=6 AND (s.is_auxiliary IS NULL OR s.is_auxiliary<>1)
          AND NOT EXISTS (SELECT 1 FROM jbx_book_subject child WHERE child.book_id=s.book_id
              AND child.deleted='n' AND child.code<>s.code AND child.code LIKE CONCAT(s.code,'%')
              AND (child.is_auxiliary IS NULL OR child.is_auxiliary<>1))
        """)
    StatementSubjectBalance unclosedProfit(@Param("bookId") String bookId, @Param("yearPeriod") String yearPeriod);
    List<StatementSubjectBalance> groupCodeSubjectBalance(@Param("dto") StatementParamsDto dto,
                                                          @Param("allMonths") List<String> allMonths,
                                                          @Param("minPeriod") String minPeriod,
                                                          @Param("maxPeriod") String maxPeriod);

    List<StatementSubjectBalance> getGeneralLedger(@Param("dto") StatementParamsDto dto,
                                                   @Param("allMonths") List<String> allMonths,
                                                   @Param("minPeriod") String minPeriod,
                                                   @Param("maxPeriod") String maxPeriod);

    List<StatementSubjectBalance> getTAccountDetails(@Param("dto") StatementParamsDto dto,
                                                     @Param("allMonths") List<String> allMonths,
                                                     @Param("minPeriod") String minPeriod,
                                                     @Param("maxPeriod") String maxPeriod);

    /**
     * 获取期初余额
     * @param dto 查询参数
     * @param startMonth 开始月份，格式：YYYY-MM
     * @return 期初余额列表
     */
    List<StatementSubjectBalance> getOpeningBalance(@Param("dto") StatementParamsDto dto, @Param("startMonth") String startMonth);

    List<StatementSubjectBalance> getClosingBalance(@Param("dto") StatementParamsDto dto, @Param("endMonth") String endMonth);

    long recoverInitial(@Param("bookId") String bookId,@Param("yearPeriod") String yearPeriod);
}
