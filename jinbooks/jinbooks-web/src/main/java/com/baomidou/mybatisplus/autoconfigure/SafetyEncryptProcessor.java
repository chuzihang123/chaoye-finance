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
 */

package com.baomidou.mybatisplus.autoconfigure;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.AES;

/**
 * 占位实现,用于补齐 MyBatis-Plus 3.5.x 的 spring.factories 中
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} 注册但未打包 class 文件的
 * {@code SafetyEncryptProcessor} (3.5.13/3.5.14/3.5.15/3.5.16/3.5.17 全部中招).
 *
 * <p>行为与 MyBatis-Plus 官方原实现一致:
 * <ol>
 *   <li>从命令行参数读取 {@code mpw.key} 作为 AES 密钥</li>
 *   <li>扫描所有 {@link OriginTrackedMapPropertySource} 中的属性值,识别 {@code mpw:} 前缀的密文</li>
 *   <li>用密钥解密后,作为最高优先级 ({@code custom-encrypt}) 注入到 Environment</li>
 * </ol>
 *
 * <p>本项目当前未启用 mpw 加密,该 processor 在 {@code mpw.key} 未提供时为 no-op.
 *
 * @see <a href="https://github.com/baomidou/mybatis-plus/issues">MyBatis-Plus Issue</a>
 */
@SuppressWarnings("removal")
public class SafetyEncryptProcessor implements EnvironmentPostProcessor, Ordered {

    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mpwKey = null;
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof SimpleCommandLinePropertySource source) {
                mpwKey = source.getProperty("mpw.key");
                break;
            }
        }
        if (!StringUtils.hasText(mpwKey)) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof OriginTrackedMapPropertySource source) {
                for (String name : source.getPropertyNames()) {
                    Object value = source.getProperty(name);
                    if (value instanceof String str && str.startsWith("mpw:")) {
                        map.put(name, AES.decrypt(str.substring(4), mpwKey));
                    }
                }
            }
        }
        if (!map.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("custom-encrypt", map));
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}
