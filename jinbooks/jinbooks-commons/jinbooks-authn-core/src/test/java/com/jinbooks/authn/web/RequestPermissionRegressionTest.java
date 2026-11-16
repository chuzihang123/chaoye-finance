package com.jinbooks.authn.web;

import com.jinbooks.entity.idm.UserInfo;
import com.jinbooks.entity.permissions.Resources;
import com.jinbooks.persistence.mapper.PermissionMapper;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class RequestPermissionRegressionTest {
    @Test
    public void writeRequiresTheGrantedMethodAndPath() {
        Resources api = new Resources();
        api.setRequestMethod("POST");
        api.setRequestUrl("/voucher/draft");
        RequestPermissionService service = service(new AtomicBoolean(false), new AtomicBoolean(true), List.of(api));
        assertTrue(service.canWrite(user(), "POST", "/voucher/draft"));
        assertFalse(service.canWrite(user(), "DELETE", "/voucher/draft"));
        assertFalse(service.canWrite(user(), "POST", "/voucher/submit"));
    }

    @Test
    public void removalTakesEffectWithoutReauthentication() {
        AtomicBoolean member = new AtomicBoolean(true);
        Resources api = new Resources();
        api.setRequestMethod("POST");
        api.setRequestUrl("/voucher/draft");
        RequestPermissionService service = service(new AtomicBoolean(false), member, List.of(api));
        assertTrue(service.canWrite(user(), "POST", "/voucher/draft"));
        member.set(false);
        assertFalse(service.canWrite(user(), "POST", "/voucher/draft"));
    }

    @Test
    public void administratorsAreCheckedFromCurrentMembership() {
        AtomicBoolean administrator = new AtomicBoolean(true);
        RequestPermissionService service = service(administrator, new AtomicBoolean(false), List.of());
        assertTrue(service.canWrite(user(), "POST", "/voucher/draft"));
        administrator.set(false);
        assertFalse(service.canWrite(user(), "POST", "/voucher/draft"));
    }

    private UserInfo user() {
        UserInfo user = new UserInfo();
        user.setId("user-1");
        user.setBookId("book-a");
        return user;
    }

    private RequestPermissionService service(AtomicBoolean administrator, AtomicBoolean member, List<Resources> resources) {
        PermissionMapper mapper = (PermissionMapper) Proxy.newProxyInstance(PermissionMapper.class.getClassLoader(),
                new Class<?>[]{PermissionMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "countAdministratorRoles" -> administrator.get() ? 1L : 0L;
                    case "countBookAccess" -> "book-a".equals(args[1]) ? 1L : 0L;
                    case "authorizedApiResources" -> member.get() ? resources : List.of();
                    default -> throw new AssertionError(method.getName());
                });
        return new RequestPermissionService(mapper);
    }
}
