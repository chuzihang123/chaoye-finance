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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinbooks.entity.ExcelImport;
import com.jinbooks.entity.Message;
import com.jinbooks.entity.dto.ListIdsDto;
import com.jinbooks.entity.hr.EmployeeTaxDeduction;
import com.jinbooks.entity.hr.dto.EmployeeTaxDeductionDto;
import com.jinbooks.entity.hr.dto.EmployeeTaxDeductionPageDto;
import com.jinbooks.entity.idm.UserInfo;
import com.jinbooks.persistence.mapper.EmployeeTaxDeductionMapper;
import com.jinbooks.persistence.service.EmployeeTaxDeductionService;
import com.jinbooks.persistence.mapper.BookMapper;
import com.jinbooks.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.lang3.StringUtils;
import com.jinbooks.util.ExcelUtils;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;


@Service
public class EmployeeTaxDeductionServiceImpl extends ServiceImpl<EmployeeTaxDeductionMapper, EmployeeTaxDeduction> implements EmployeeTaxDeductionService {
    @Autowired
    BookMapper bookMapper;

    @Override
    public Message<Page<EmployeeTaxDeduction>> pageList(EmployeeTaxDeductionPageDto dto) {
        Page<EmployeeTaxDeduction> page = this.getBaseMapper().pageList(dto.build(), dto);
        return new Message<>(Message.SUCCESS, page);
    }

    @Override
    @Transactional
    public Message<String> save(EmployeeTaxDeductionDto dto) {

        EmployeeTaxDeduction model = new EmployeeTaxDeduction();
        BeanUtil.copyProperties(dto, model);


        boolean saveResult = super.save(model);

        return saveResult ? new Message<>(Message.SUCCESS, "新增成功") : new Message<>(Message.FAIL, "新增失败");
    }

    @Override
    @Transactional
    public Message<String> update(EmployeeTaxDeductionDto dto) {
        String id = dto.getId();
        EmployeeTaxDeduction model = super.getById(id);

        BeanUtil.copyProperties(dto, model);
        boolean result = super.updateById(model);
        return result ? new Message<>(Message.SUCCESS, "修改成功") : new Message<>(Message.FAIL, "修改失败");
    }

    @Override
    @Transactional
    public Message<String> delete(ListIdsDto dto) {
        List<String> listIds = dto.getListIds();
        boolean result = false;
        result = super.removeBatchByIds(listIds);

        return result ? new Message<>(Message.SUCCESS, "删除成功") : new Message<>(Message.FAIL, "删除失败");
    }

    @Override
    @Transactional
    public void importFromExcel(ExcelImport excelImportFile, UserInfo currentUser) {
        if (!excelImportFile.isExcelNotEmpty()) throw new BusinessException(400, "请选择扣除资料Excel文件");
        Map<String, EmployeeTaxDeduction> rows = new LinkedHashMap<>();
        try {
            Workbook workbook = excelImportFile.biuldWorkbook();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || (StringUtils.isBlank(ExcelUtils.getValue(row, 0))
                            && StringUtils.isBlank(ExcelUtils.getValue(row, 1))
                            && StringUtils.isBlank(ExcelUtils.getValue(row, 3)))) continue;
                    try {
                        EmployeeTaxDeduction model = buildFromSheetRow(row, currentUser);
                        if (StringUtils.isBlank(model.getEmployeeName()) || StringUtils.isBlank(model.getIdCardNo())) {
                            throw new IllegalArgumentException("姓名和证件号不能为空");
                        }
                        rows.putIfAbsent(model.getIdCardNo(), model);
                    } catch (RuntimeException invalidRow) {
                        throw new BusinessException(400, "工作表[" + sheet.getSheetName() + "]第" + (rowIndex + 1)
                                + "行数据错误，请核对姓名、证件号及扣除金额");
                    }
                }
            }
            if (rows.isEmpty()) throw new BusinessException(400, "文件中没有可导入的扣除资料，原数据已保留");
            if (StringUtils.isBlank(currentUser.getBookId()) || bookMapper.selectForUpdate(currentUser.getBookId()) == null) {
                throw new BusinessException(400, "当前账套不存在");
            }
            this.remove(new LambdaQueryWrapper<EmployeeTaxDeduction>()
                    .eq(EmployeeTaxDeduction::getBookId, currentUser.getBookId())
                    .isNotNull(EmployeeTaxDeduction::getIdCardNo));
            if (!this.saveBatch(rows.values())) throw new BusinessException(400, "扣除资料保存失败，导入已回滚");
        } catch (IOException invalidFile) {
            throw new BusinessException(400, "Excel文件无法读取，原数据已保留");
        } catch (BusinessException invalidData) {
            throw invalidData;
        } catch (RuntimeException invalidFile) {
            throw new BusinessException(400, "Excel文件格式不正确，原数据已保留");
        } finally {
            excelImportFile.closeWorkbook();
        }
    }

    /**
     * 江苏税务个人扣除
     * @param row
     * @param currentUser
     * @return
     */
    private EmployeeTaxDeduction buildFromSheetRow(Row row, UserInfo currentUser) {
        EmployeeTaxDeduction taxDeduction = new EmployeeTaxDeduction();
        taxDeduction.setCreatedDate(new Date());
        taxDeduction.setBookId(currentUser.getBookId());
        taxDeduction.setEmployeeNo(ExcelUtils.getValue(row, 0));
        taxDeduction.setEmployeeName(ExcelUtils.getValue(row, 1));
        taxDeduction.setIdCardType(ExcelUtils.getValue(row, 2));
        taxDeduction.setIdCardNo(ExcelUtils.getValue(row, 3));

        taxDeduction.setEducation(ExcelUtils.getDoubleValue(row, 12));
        taxDeduction.setContinuingEducation(ExcelUtils.getDoubleValue(row, 13));
        taxDeduction.setHousingLoan(ExcelUtils.getDoubleValue(row, 14));
        taxDeduction.setRent(ExcelUtils.getDoubleValue(row, 15));
        taxDeduction.setElderlyCare(ExcelUtils.getDoubleValue(row, 16));
        taxDeduction.setInfantsCare(ExcelUtils.getDoubleValue(row, 17));
        taxDeduction.setIndividualPension(ExcelUtils.getDoubleValue(row, 18));
        taxDeduction.setEnterprisePension(ExcelUtils.getDoubleValue(row, 19));
        taxDeduction.setCommercialHealth(ExcelUtils.getDoubleValue(row, 20));
        taxDeduction.setDeferredPension(ExcelUtils.getDoubleValue(row, 21));
        taxDeduction.setOthers(ExcelUtils.getDoubleValue(row, 21));

        return taxDeduction;
    }
}
