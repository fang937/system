package com.hbsd.labselect.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关装配。
 *
 * <p>只要 providers 里任意一个通道填了 API Key，就启用多通道网关（额度耗尽自动切换）；
 * 一个 Key 都没有时回落到内置规则引擎，接口结构保持不变。</p>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public AiGateway aiGateway(AiProperties properties, ObjectMapper objectMapper) {
        if (properties.hasAnyKey()) {
            return new MultiProviderAiGateway(properties, objectMapper);
        }
        return new MockAiGateway();
    }
}
