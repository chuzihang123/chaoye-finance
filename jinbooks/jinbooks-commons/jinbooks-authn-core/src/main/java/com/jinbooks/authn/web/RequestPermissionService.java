package com.jinbooks.authn.web;

import com.jinbooks.entity.idm.UserInfo;
import com.jinbooks.entity.permissions.Resources;
import com.jinbooks.persistence.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Service
@RequiredArgsConstructor
public class RequestPermissionService {
    private final PermissionMapper permissionMapper;
    private final AntPathMatcher paths = new AntPathMatcher();

    public boolean isAdministrator(UserInfo user) {
        return permissionMapper.countAdministratorRoles(user.getId()) > 0;
    }

    public boolean canAccessBook(UserInfo user, String bookId) {
        return bookId != null && (isAdministrator(user)
                || permissionMapper.countBookAccess(user.getId(), bookId) > 0);
    }

    public boolean canWrite(UserInfo user, String method, String path) {
        if (isAdministrator(user)) return true;
        for (Resources resource : permissionMapper.authorizedApiResources(user.getId(), user.getBookId())) {
            String configuredMethod = resource.getRequestMethod();
            if (resource.getRequestUrl() != null
                    && ("*".equals(configuredMethod) || method.equalsIgnoreCase(configuredMethod))
                    && paths.match(resource.getRequestUrl(), path)) {
                return true;
            }
        }
        return false;
    }
}
