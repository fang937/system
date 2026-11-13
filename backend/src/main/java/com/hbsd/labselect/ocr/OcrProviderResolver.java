package com.hbsd.labselect.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历解析引擎的运行时选择器。
 *
 * <p>优先级：`system_config.ocr.provider`（管理端可改）> `.env` 的 `OCR_PROVIDER` > `auto`。
 * 结果带 10 秒缓存，管理端保存后立刻刷新缓存，无需重启后端。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrProviderResolver {

    public static final String CONFIG_KEY = "ocr.provider";

    /** 可选引擎，顺序即自动模式下的尝试顺序 */
    public static final List<String> OPTIONS = List.of("auto", "mineru", "pp-structure-v3", "ocr", "none");

    private static final long CACHE_TTL_MS = 10_000L;

    private final JdbcTemplate jdbc;
    private final OcrProperties properties;

    private volatile String cached;
    private volatile long cachedAt;

    /** 当前生效的引擎。 */
    public String current() {
        long now = System.currentTimeMillis();
        String snapshot = cached;
        if (snapshot != null && now - cachedAt < CACHE_TTL_MS) {
            return snapshot;
        }
        String value = readFromDb();
        if (value == null || value.isBlank()) {
            value = properties.getProvider();
        }
        if (value == null || value.isBlank()) {
            value = "auto";
        }
        value = normalize(value);
        cached = value;
        cachedAt = now;
        return value;
    }

    /** 管理端保存选择；非法值直接拒绝。 */
    public void update(String provider) {
        String value = normalize(provider);
        if (!OPTIONS.contains(value)) {
            throw new com.hbsd.labselect.common.BizException("不支持的解析引擎：" + provider);
        }
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, CONFIG_KEY);
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE system_config SET config_value = ?, description = ? WHERE config_key = ?",
                    value, "简历 OCR 引擎（管理端可切换）", CONFIG_KEY);
        } else {
            jdbc.update("""
                    INSERT INTO system_config (config_key, config_value, value_type, config_group, description)
                    VALUES (?, ?, 'STRING', 'ocr', '简历 OCR 引擎（管理端可切换）')
                    """, CONFIG_KEY, value);
        }
        cached = value;
        cachedAt = System.currentTimeMillis();
        log.info("简历解析引擎切换为 {}", value);
    }

    /** 管理端展示：当前选择 + 各引擎是否具备调用条件。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", current());
        result.put("envProvider", properties.getProvider());
        result.put("options", OPTIONS);

        Map<String, Object> available = new LinkedHashMap<>();
        available.put("mineru", properties.mineruConfigured());
        available.put("pp-structure-v3",
                properties.getStructureEndpoint() != null && !properties.getStructureEndpoint().isBlank());
        available.put("ocr", properties.getEndpoint() != null && !properties.getEndpoint().isBlank());
        result.put("available", available);

        result.put("model", properties.getMineruModel());
        result.put("mineruEndpoint", properties.getMineruEndpoint());
        return result;
    }

    /** 自动模式下的尝试顺序；非 auto 时只返回该引擎本身。 */
    public List<String> plan() {
        String provider = current();
        if ("none".equalsIgnoreCase(provider)) {
            return List.of();
        }
        if ("auto".equalsIgnoreCase(provider)) {
            return List.of("mineru", "pp-structure-v3", "ocr");
        }
        return List.of(provider);
    }

    private String readFromDb() {
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT config_value FROM system_config WHERE config_key = ?", String.class, CONFIG_KEY);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.debug("读取 {} 失败，回退环境变量：{}", CONFIG_KEY, e.getMessage());
            return null;
        }
    }

    private String normalize(String provider) {
        return provider == null ? "auto" : provider.trim().toLowerCase();
    }
}
