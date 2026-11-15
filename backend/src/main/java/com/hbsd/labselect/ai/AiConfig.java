package com.hbsd.labselect.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关装配。
 *
 * <p>默认启用多通道网关（额度耗尽自动切换；一把 Key 都没有时由网关内部回落规则引擎，
 * 保证管理端随时写入 Key 即可激活真实通道，无需重启）；仅当 provider=mock 或总开关关闭时强制规则引擎。</p>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public AiGateway aiGateway(AiProperties properties, ObjectMapper objectMapper) {
        if (!properties.isEnabled() || "mock".equalsIgnoreCase(properties.getProvider())) {
            return new MockAiGateway();
        }
        return new MultiProviderAiGateway(properties, objectMapper);
    }
}
