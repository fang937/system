package com.hbsd.labselect.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsd.labselect.common.BizException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 多通道网关：同时挂多把 Key，一个通道额度耗尽自动切下一个。
 *
 * <p>切换规则：</p>
 * <ol>
 *   <li>首选 active 指定的通道，其余按配置顺序；</li>
 *   <li>命中额度/配额/限流错误（402、429、insufficient_quota 等）→ 标记该通道额度不足并冷却，切换下一个；</li>
 *   <li>网络或格式错误 → 记录错误后同样尝试下一个（保证可用性）；</li>
 *   <li>全部不可用 → 抛出 429，前端显示红色提示，管理端可手动切换通道。</li>
 * </ol>
 */
@Slf4j
public class MultiProviderAiGateway implements AiGateway {

    private final AiProperties properties;
    private final List<Slot> slots = new ArrayList<>();
    private final ThreadLocal<String> lastUsed = new ThreadLocal<>();
    private volatile String activeName;

    public MultiProviderAiGateway(AiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        for (AiProperties.Provider config : properties.allProviders()) {
            Slot slot = new Slot(config);
            if (config.hasKey()) {
                slot.gateway = new OpenAiCompatibleGateway(properties, config, mapper);
            }
            slots.add(slot);
        }
        this.activeName = resolveInitialActive();
    }

    private String resolveInitialActive() {
        String wanted = properties.getActive();
        if (wanted != null && !wanted.isBlank()) {
            for (Slot slot : slots) {
                if (wanted.equalsIgnoreCase(slot.config.getName()) && slot.gateway != null) {
                    return slot.config.getName();
                }
            }
        }
        return slots.stream()
                .filter(slot -> slot.gateway != null)
                .map(slot -> slot.config.getName())
                .findFirst()
                .orElse(slots.isEmpty() ? "" : slots.get(0).config.getName());
    }

    /* ---------------- AiGateway ---------------- */

    @Override
    public String provider() {
        return "multi";
    }

    @Override
    public String model() {
        Slot slot = slotByName(lastUsed.get());
        if (slot == null) {
            slot = slotByName(activeName);
        }
        return slot == null ? "" : slot.modelName();
    }

    @Override
    public String activeName() {
        String used = lastUsed.get();
        return used != null ? used : activeName;
    }

    @Override
    public Map<String, Object> interviewTips(Map<String, Object> input) {
        return execute(gateway -> gateway.interviewTips(input));
    }

    @Override
    public Map<String, Object> recommend(Map<String, Object> input) {
        return execute(gateway -> gateway.recommend(input));
    }

    @Override
    public Map<String, Object> resumeReview(Map<String, Object> input) {
        return execute(gateway -> gateway.resumeReview(input));
    }

    @Override
    public Map<String, Object> avatarChat(Map<String, Object> input) {
        return execute(gateway -> gateway.avatarChat(input));
    }

    @Override
    public List<Map<String, Object>> providers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Slot slot : slots) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", slot.config.getName());
            row.put("model", slot.modelName());
            row.put("baseUrl", slot.baseUrl());
            row.put("keyMasked", mask(slot.config.getApiKey()));
            row.put("configured", slot.config.hasKey());
            row.put("active", slot.config.getName().equals(activeName));
            row.put("quotaExceeded", slot.quotaExceeded);
            row.put("available", slot.available());
            row.put("cooldownUntil", slot.cooldownUntil == null ? null : slot.cooldownUntil.toString());
            row.put("lastError", slot.lastError);
            row.put("lastErrorAt", slot.lastErrorAt == null ? null : slot.lastErrorAt.toString());
            row.put("successCount", slot.successCount);
            row.put("failureCount", slot.failureCount);
            result.add(row);
        }
        return result;
    }

    @Override
    public void switchProvider(String name) {
        Slot slot = slotByName(name);
        if (slot == null) {
            throw new BizException("通道不存在：" + name);
        }
        if (slot.gateway == null) {
            throw new BizException("通道「" + name + "」尚未配置 API Key");
        }
        slot.quotaExceeded = false;
        slot.cooldownUntil = null;
        slot.lastError = null;
        activeName = slot.config.getName();
        log.info("AI 通道已切换为 {}", activeName);
    }

    /* ---------------- 管理端调试 ---------------- */

    @Override
    public List<String> listModels(String name) {
        return requireGateway(name).listModels();
    }

    @Override
    public Map<String, Object> testCall(String name) {
        return requireGateway(name).testCall();
    }

    @Override
    public void setModel(String name, String model) {
        Slot slot = slotByName(name);
        if (slot == null) {
            throw new BizException("通道不存在：" + name);
        }
        if (model == null || model.isBlank()) {
            throw new BizException("模型名不能为空");
        }
        slot.config.setModel(model.trim());
        log.info("AI 通道 {} 模型切换为 {}", slot.config.getName(), model.trim());
    }

    private OpenAiCompatibleGateway requireGateway(String name) {
        Slot slot = slotByName(name);
        if (slot == null) {
            throw new BizException("通道不存在：" + name);
        }
        if (slot.gateway == null) {
            throw new BizException("通道「" + name + "」尚未配置 API Key");
        }
        return slot.gateway;
    }

    /* ---------------- 调用与容错 ---------------- */

    private Map<String, Object> execute(Function<AiGateway, Map<String, Object>> call) {
        lastUsed.remove();
        List<String> failures = new ArrayList<>();
        boolean quotaHit = false;

        for (Slot slot : orderedSlots()) {
            if (slot.gateway == null) {
                continue;
            }
            if (!slot.available()) {
                failures.add(slot.config.getName() + "：额度冷却中");
                quotaHit = quotaHit || slot.quotaExceeded;
                continue;
            }
            try {
                Map<String, Object> data = call.apply(slot.gateway);
                slot.successCount++;
                slot.lastError = null;
                slot.quotaExceeded = false;
                slot.cooldownUntil = null;
                lastUsed.set(slot.config.getName());
                // 实际承担调用的通道即为当前通道，状态展示与后续排序以此为准
                activeName = slot.config.getName();
                return data;
            } catch (AiQuotaException e) {
                slot.failureCount++;
                slot.quotaExceeded = true;
                slot.lastError = e.getMessage();
                slot.lastErrorAt = LocalDateTime.now();
                slot.cooldownUntil = LocalDateTime.now().plusMinutes(Math.max(1, properties.getQuotaCooldownMinutes()));
                quotaHit = true;
                failures.add(slot.config.getName() + "：额度不足，已切换下一通道");
                log.warn("[{}] 额度不足，冷却 {} 分钟", slot.config.getName(), properties.getQuotaCooldownMinutes());
            } catch (BizException e) {
                slot.failureCount++;
                slot.lastError = e.getMessage();
                slot.lastErrorAt = LocalDateTime.now();
                failures.add(slot.config.getName() + "：" + e.getMessage());
            } catch (Exception e) {
                slot.failureCount++;
                slot.lastError = e.getMessage();
                slot.lastErrorAt = LocalDateTime.now();
                failures.add(slot.config.getName() + "：" + e.getMessage());
            }
        }

        String detail = failures.isEmpty() ? "没有可用通道（未配置 API Key）" : String.join("；", failures);
        String hint = quotaHit ? "所有 AI 通道额度均不足，请联系管理员切换通道或补充额度" : "所有 AI 通道调用失败";
        throw new BizException(quotaHit ? 429 : 502, hint + "（" + detail + "）");
    }

    private List<Slot> orderedSlots() {
        List<Slot> ordered = new ArrayList<>();
        Slot active = slotByName(activeName);
        if (active != null) {
            ordered.add(active);
        }
        for (Slot slot : slots) {
            if (slot != active) {
                ordered.add(slot);
            }
        }
        return ordered;
    }

    private Slot slotByName(String name) {
        if (name == null) {
            return null;
        }
        for (Slot slot : slots) {
            if (name.equalsIgnoreCase(slot.config.getName())) {
                return slot;
            }
        }
        return null;
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final class Slot {
        private final AiProperties.Provider config;
        private OpenAiCompatibleGateway gateway;
        private volatile boolean quotaExceeded;
        private volatile String lastError;
        private volatile LocalDateTime lastErrorAt;
        private volatile LocalDateTime cooldownUntil;
        private volatile int successCount;
        private volatile int failureCount;

        private Slot(AiProperties.Provider config) {
            this.config = config;
        }

        private String modelName() {
            if (config.getModel() != null && !config.getModel().isBlank()) {
                return config.getModel();
            }
            return gateway == null ? "" : gateway.model();
        }

        private String baseUrl() {
            return config.getBaseUrl() == null ? "" : config.getBaseUrl();
        }

        private boolean available() {
            return gateway != null && (cooldownUntil == null || LocalDateTime.now().isAfter(cooldownUntil));
        }

        @SuppressWarnings("unused")
        private String cooldownText() {
            return cooldownUntil == null ? "" : cooldownUntil.format(TIME);
        }
    }
}
