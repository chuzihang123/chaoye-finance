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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 占位实现,用于补齐 MyBatis-Plus 3.5.x starter 的
 * {@code AutoConfiguration.imports} 中注册但未打包 class 文件的 4 个 auto-config 类.
 *
 * <p>实际意义仅在 spring boot 4.1 的 {@code AutoConfigurationSorter} 排序阶段 —
 * 它需要读这些类的 {@code @AutoConfigureOrder} 注解,class 缺失会抛
 * {@code IllegalStateException: Unable to read meta-data}.
 *
 * <p>本项目已用 {@code com.jinbooks.autoconfigure.MybatisPlusConfiguration} 自行接管
 * 全部 MyBatis-Plus 装配逻辑(SqlSessionFactory / MybatisPlusInterceptor / IdentifierGenerator /
 * 事务 / MetaObjectHandler),所以这些占位类不注册任何 bean,只是让 spring boot 4.1
 * 的 sort 阶段能顺利读 class metadata.
 */
@Configuration
@AutoConfiguration
public class MybatisPlusInnerInterceptorAutoConfiguration {
}
