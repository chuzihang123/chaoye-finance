/*
 * Copyright [2025] [JinBooks of copyright http://www.jinbooks.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.baomidou.mybatisplus.autoconfigure;

import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitializationDetector;

/**
 * 占位实现,见 {@link SafetyEncryptProcessor} 同包说明.
 *
 * <p>由 MyBatis-Plus 3.5.x spring.factories 注册到
 * {@code org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitializationDetector} 键,
 * SpringFactoriesLoader 在加载时会校验实现必须 implements 该接口.
 * 本项目不使用 mpw 加密,返回空集即可.
 */
public class MybatisDependsOnDatabaseInitializationDetector implements DependsOnDatabaseInitializationDetector {

    @Override
    public Set<String> detect(ConfigurableListableBeanFactory beanFactory) {
        return Collections.emptySet();
    }
}
