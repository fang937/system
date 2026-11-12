package com.hbsd.labselect.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 能力配置，支持同时配置多个供应商（多把 Key），用于额度互相备份。
 *
 * <p>启用规则：</p>
 * <ol>
 *   <li>provider = mock 或 enabled = false → 强制内置规则引擎；</li>
 *   <li>providers 里任一通道填了 api-key → 使用多通道网关，按顺序自动切换；</li>
 *   <li>providers 全空但填了旧的 app.ai.api-key → 视为单通道，保持向后兼容；</li>
 *   <li>一个 Key 都没有 → 回落到 mock，接口结构不变。</li>
 * </ol>
 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 总开关，作为应急熔断：false 时一律走 mock。 */
    private boolean enabled = true;

    /** auto / mock；auto 时按是否配置了 Key 决定实现 */
    private String provider = "auto";

    /** 首选通道名称，对应 providers[].name；为空则按配置顺序 */
    private String active = "";

    /** 多通道配置 */
    private List<Provider> providers = new ArrayList<>();

    /** 额度耗尽后的冷却时间（分钟），冷却期内不再尝试该通道 */
    private int quotaCooldownMinutes = 10;

    /** 单次调用超时（秒） */
    private int timeoutSeconds = 30;

    /** 采样温度 */
    private double temperature = 0.3;

    /** 是否要求模型返回 JSON（个别网关不支持时置 false） */
    private boolean jsonMode = true;

    /** 兼容旧配置：单通道 */
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "gpt-4o-mini";

    /** 全局系统提示词：所有能力共用的身份与边界 */
    private String systemPrompt = """
            你是河北师范大学实训室双向选择系统的教学助理。回答只依据系统提供的导师资料与学生资料，
            不编造名额、不承诺录取结果、不透露面试题目、不评价其他导师。信息不足时明确说明。
            """;

    /** 是否存在可用的真实通道 */
    public boolean hasAnyKey() {
        if (!enabled || "mock".equalsIgnoreCase(provider)) {
            return false;
        }
        if (providers.stream().anyMatch(p -> p != null && p.hasKey())) {
            return true;
        }
        return apiKey != null && !apiKey.isBlank();
    }

    /** 全部通道（含未配置 Key 的，用于管理端展示）；无通道配置时回退为旧单通道 */
    public List<Provider> allProviders() {
        List<Provider> list = new ArrayList<>();
        for (Provider item : providers) {
            if (item != null && item.getName() != null && !item.getName().isBlank()) {
                list.add(item);
            }
        }
        boolean anyKey = list.stream().anyMatch(Provider::hasKey);
        if (anyKey) {
            return list;
        }
        // 通道都还没填 Key，但旧写法填了 api-key：按旧单通道处理，避免两套配置互相干扰
        if (apiKey != null && !apiKey.isBlank()) {
            List<Provider> legacy = new ArrayList<>();
            Provider item = new Provider();
            item.setName("default");
            item.setBaseUrl(baseUrl);
            item.setApiKey(apiKey);
            item.setModel(model);
            legacy.add(item);
            return legacy;
        }
        return list;
    }

    @Data
    public static class Provider {
        /** 通道标识，用于切换与展示，如 openai / deepseek / qwen */
        private String name;
        private String baseUrl;
        private String apiKey;
        private String model;

        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
