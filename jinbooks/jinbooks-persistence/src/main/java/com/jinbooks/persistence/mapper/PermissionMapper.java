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
 





/**
 *
 */
package com.jinbooks.persistence.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinbooks.entity.permissions.Permission;
import com.jinbooks.entity.permissions.Resources;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author Crystal.sea
 *
 */

public  interface PermissionMapper extends BaseMapper<Permission> {
    @Select("""
        SELECT COUNT(*) FROM jbx_roles r JOIN jbx_role_member m ON m.role_id=r.id
        WHERE m.member_id=#{userId} AND r.deleted='n' AND r.status=1
          AND r.category IN ('supervisor','administrator')
        """)
    long countAdministratorRoles(@Param("userId") String userId);

    @Select("""
        SELECT COUNT(*) FROM jbx_permission_book p JOIN jbx_book b ON p.book_id=b.id
        WHERE p.user_id=#{userId} AND p.book_id=#{bookId} AND p.deleted='n' AND b.deleted='n'
        """)
    long countBookAccess(@Param("userId") String userId, @Param("bookId") String bookId);

    @Select("""
        SELECT DISTINCT res.* FROM jbx_resources res
        JOIN jbx_permission p ON p.resource_id=res.id AND p.status=1
        JOIN jbx_roles r ON r.id=p.role_id AND r.deleted='n' AND r.status=1
        LEFT JOIN jbx_role_member m ON m.role_id=r.id AND m.member_id=#{userId}
        WHERE p.book_id=#{bookId} AND res.deleted='n' AND res.status=1
          AND (r.role_code='ROLE_ALL_USER' OR (m.member_id IS NOT NULL AND m.book_id=#{bookId}))
          AND (res.classify='API' OR res.action_type='w')
        """)
    List<Resources> authorizedApiResources(@Param("userId") String userId, @Param("bookId") String bookId);

    public int insertPermissions(List<Permission> permissionList);

    public int deletePermissions(List<Permission> permissionList);

    public List<Permission> queryPermissions(Permission permission);

}
