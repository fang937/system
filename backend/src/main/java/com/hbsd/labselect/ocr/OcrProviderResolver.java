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

    /** 管理端可写入的 OCR 密钥/接入点：system_config 键前缀 ocr.secret.<target>（页面写入优先于 .env） */
    public static final String SECRET_KEY_PREFIX = "ocr.secret.";

    /** 可写入的密钥目标 */
    public static final List<String> SECRET_TARGETS = List.of("mineru-token", "structure-endpoint", "structure-api-key");

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

    /* ---------------- 密钥/接入点写入（管理端） ---------------- */

    /**
     * 写入 OCR 密钥或接入点：立即生效并持久化到 system_config（优先于 .env）。
     * value 传空串表示清空（回退到 .env 的初始值由下次重启时的绑定顺序决定，运行时即清）。
     */
    public void updateSecret(String target, String value) {
        String key = normalizeTarget(target);
        String val = value == null ? "" : value.trim();
        applySecret(key, val);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, SECRET_KEY_PREFIX + key);
        if (exists != null && exists > 0) {
            if (val.isEmpty()) {
                jdbc.update("DELETE FROM system_config WHERE config_key = ?", SECRET_KEY_PREFIX + key);
            } else {
                jdbc.update("UPDATE system_config SET config_value = ?, description = ? WHERE config_key = ?",
                        val, "OCR 密钥/接入点（管理端写入，优先于 .env）", SECRET_KEY_PREFIX + key);
            }
        } else if (!val.isEmpty()) {
            jdbc.update("""
                    INSERT INTO system_config (config_key, config_value, value_type, config_group, description)
                    VALUES (?, ?, 'STRING', 'ocr', 'OCR 密钥/接入点（管理端写入，优先于 .env）')
                    """, SECRET_KEY_PREFIX + key, val);
        }
        log.info("OCR 配置 {} 已更新（{}）", key, mask(val));
    }

    /** 启动时恢复管理端写入过的密钥/接入点 */
    @jakarta.annotation.PostConstruct
    void restoreSecrets() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT config_key, config_value FROM system_config WHERE config_key LIKE ?",
                    SECRET_KEY_PREFIX + "%");
            for (Map<String, Object> row : rows) {
                String configKey = String.valueOf(row.get("config_key"));
                String value = row.get("config_value") == null ? "" : String.valueOf(row.get("config_value"));
                if (value.isBlank()) {
                    continue;
                }
                try {
                    applySecret(configKey.substring(SECRET_KEY_PREFIX.length()), value);
                } catch (Exception e) {
                    log.warn("恢复 OCR 配置 {} 失败：{}", configKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("读取 OCR 密钥覆盖配置失败（忽略，沿用 .env）：{}", e.getMessage());
        }
    }

    private String normalizeTarget(String target) {
        String key = target == null ? "" : target.trim().toLowerCase();
        if (!SECRET_TARGETS.contains(key)) {
            throw new com.hbsd.labselect.common.BizException("不支持的配置项：" + target);
        }
        return key;
    }

    private void applySecret(String target, String value) {
        switch (target) {
            case "mineru-token" -> properties.setMineruToken(value);
            case "structure-endpoint" -> properties.setStructureEndpoint(value);
            case "structure-api-key" -> properties.setStructureApiKey(value);
            default -> throw new com.hbsd.labselect.common.BizException("不支持的配置项：" + target);
        }
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "(空)";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
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
        // 密钥/接入点现状（掩码），供管理端「API Key 配置」展示
        result.put("secrets", Map.of(
                "mineru-token", mask(properties.getMineruToken()),
                "structure-endpoint", properties.getStructureEndpoint() == null ? "" : properties.getStructureEndpoint(),
                "structure-api-key", mask(properties.getStructureApiKey())));
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
