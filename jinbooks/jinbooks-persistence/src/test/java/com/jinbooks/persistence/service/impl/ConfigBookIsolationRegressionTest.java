package com.jinbooks.persistence.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinbooks.constants.ConstsSysConfig;
import com.jinbooks.entity.config.ConfigSys;
import com.jinbooks.exception.ServiceException;
import com.jinbooks.persistence.mapper.ConfigSysMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ConfigBookIsolationRegressionTest {
    @BeforeClass
    public static void initializeMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ConfigSys.class);
    }

    @Test
    public void currentTermUpdateAlwaysIncludesBookScope() {
        AtomicReference<ConfigSys> changed = new AtomicReference<>();
        AtomicReference<String> sql = new AtomicReference<>();
        ConfigSysServiceImpl service = service(changed, sql);
        service.updateCurrentTerm("book-a", "2026-12");
        assertEquals("book-a", changed.get().getBookId());
        assertEquals(ConstsSysConfig.SYS_PAYMENT_TERM_CURRENT, changed.get().getConfigKey());
        assertTrue(sql.get().contains("book_id"));
        assertTrue(sql.get().contains("config_key"));
    }

    @Test
    public void updateByIdAlsoIncludesBookScope() {
        AtomicReference<ConfigSys> changed = new AtomicReference<>();
        AtomicReference<String> sql = new AtomicReference<>();
        ConfigSys config = new ConfigSys();
        config.setConfigId("config-a");
        config.setBookId("book-a");
        config.setConfigKey("qa.display");
        config.setConfigValue("value");
        service(changed, sql).update(config);
        assertTrue(sql.get().contains("book_id"));
        assertTrue(sql.get().contains("config_id"));
    }

    @Test(expected = ServiceException.class)
    public void missingBookCannotBecomeGlobalUpdate() {
        ConfigSys config = new ConfigSys();
        config.setConfigKey(ConstsSysConfig.SYS_PAYMENT_TERM_CURRENT);
        config.setConfigValue("2026-12");
        service(new AtomicReference<>(), new AtomicReference<>()).update(config);
    }

    @SuppressWarnings("unchecked")
    private ConfigSysServiceImpl service(AtomicReference<ConfigSys> changed, AtomicReference<String> sql) {
        ConfigSysMapper mapper = (ConfigSysMapper) Proxy.newProxyInstance(ConfigSysMapper.class.getClassLoader(), new Class<?>[]{ConfigSysMapper.class}, (instance, method, args) -> {
            if (!method.getName().equals("update")) throw new AssertionError("Unexpected mapper call " + method.getName());
            changed.set((ConfigSys) args[0]);
            sql.set(((LambdaQueryWrapper<ConfigSys>) args[1]).getSqlSegment());
            return 1;
        });
        return new ConfigSysServiceImpl(mapper);
    }
}
