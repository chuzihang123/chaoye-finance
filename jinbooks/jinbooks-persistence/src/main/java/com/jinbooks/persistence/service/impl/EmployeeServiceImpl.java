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
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinbooks.entity.Message;
import com.jinbooks.entity.dto.ListIdsDto;
import com.jinbooks.entity.hr.Employee;
import com.jinbooks.entity.hr.dto.EmployeeChangeDto;
import com.jinbooks.entity.hr.dto.EmployeePageDto;
import com.jinbooks.persistence.mapper.EmployeeMapper;
import com.jinbooks.persistence.service.EmployeeService;
import com.jinbooks.persistence.mapper.BookMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinbooks.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 员工信息Service业务层处理
 *
 * @author wuyan
 * {@code @date} 2025-01-22
 */

@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    @Autowired
    EmployeeMapper jbxEmployeeMapper;

    @Autowired
    IdentifierGenerator identifierGenerator;
    @Autowired
    BookMapper bookMapper;
    @Autowired
    com.jinbooks.persistence.mapper.OrganizationsMapper organizationsMapper;

    /**
     * 分页查询
     *
     * @param dto 分页参数
     * @return 查询结果
     */
    @Override
    public Message<Page<Employee>> pageList(EmployeePageDto dto) {
        return Message.ok(jbxEmployeeMapper.pageList(dto.build(), dto));
    }

    /**
     * 插入数据
     *
     * @param dto 插入对象
     * @return 插入结果
     */
    @Override
    @Transactional
    public Message<String> save(EmployeeChangeDto dto) {
        validateEmployeeNumber(dto);
        Employee employee = Employee.builder().build();
        BeanUtil.copyProperties(dto, employee);
        String currentId = identifierGenerator.nextId(employee).toString();
        employee.setId(currentId);
        boolean save = super.save(employee);
        return save ? new Message<>(Message.SUCCESS, "新增成功", currentId) : new Message<>(Message.FAIL, "新增失败");
    }

    /**
     * 更新信息
     *
     * @param dto 更新对象
     * @return 结果
     */
    @Override
    @Transactional
    public Message<String> update(EmployeeChangeDto dto) {
        validateEmployeeNumber(dto);
        Employee employee = Employee.builder().build();
        BeanUtil.copyProperties(dto, employee);
        String currentId = dto.getId();
        boolean update = super.updateById(employee);
        return update ? new Message<>(Message.SUCCESS, "修改成功", currentId) : new Message<>(Message.FAIL, "修改失败");
    }


    private void validateEmployeeNumber(EmployeeChangeDto dto) {
        if (bookMapper.selectForUpdate(dto.getBookId()) == null) throw new BusinessException(400, "账套不存在");
        if (dto.getId() != null) {
            Employee existing = jbxEmployeeMapper.selectById(dto.getId());
            if (existing == null || !dto.getBookId().equals(existing.getBookId())) {
                throw new BusinessException(403, "员工不存在或不属于当前账套");
            }
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(dto.getDepartmentId())) {
            var department = organizationsMapper.selectById(dto.getDepartmentId());
            if (department == null || !dto.getBookId().equals(department.getBookId())) {
                throw new BusinessException(400, "所属部门不存在或不属于当前账套");
            }
        }
        if (jbxEmployeeMapper.exists(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getBookId, dto.getBookId()).eq(Employee::getEmployeeNumber, dto.getEmployeeNumber())
                .ne(dto.getId() != null, Employee::getId, dto.getId()))) {
            throw new BusinessException(400, "本账套工号重复");
        }
    }

    /**
     * 根据ID删除
     *
     * @param dto ID组
     * @return 结果
     */
    @Override
    @Transactional
    public Message<String> delete(ListIdsDto dto) {
        List<String> ids = dto.getListIds();
        boolean result = super.removeBatchByIds(ids);
        return result ? new Message<>(Message.SUCCESS, "删除成功") : new Message<>(Message.FAIL, "删除失败");
    }
}
