package com.hbsd.labselect.ocr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 简历正文抽取配置。
 *
 * <p>策略：PDF 先抽文本层（无 OCR，快且准）；文本层太少（扫描件/图片版）时再调用
 * PaddleOCR 的 PP-StructureV3 文档解析。未配置 endpoint 时仅使用文本层，并在结果里注明。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    /**
     * auto：优先 PP-StructureV3，失败再退回普通 OCR；
     * pp-structure-v3：只用文档解析；ocr：只用普通识别；none：全部禁用。
     */
    private String provider = "auto";

    /** PP-StructureV3 服务地址，例如 http://127.0.0.1:8080/layout-parsing */
    private String structureEndpoint = "";

    /** PP-StructureV3 鉴权方式：token（飞桨/星河社区）/ Bearer / none */
    private String structureAuthScheme = "token";

    /** PP-StructureV3 专用 Key，留空则复用 apiKey */
    private String structureApiKey = "";

    /** 是否识别表格（简历里的成绩单、项目表格靠它） */
    private boolean structureTable = true;

    /** 是否识别公式（简历一般用不上，开启会更慢） */
    private boolean structureFormula = false;

    /** 普通 OCR 服务地址，例如 http://127.0.0.1:8868/predict/ocr_system */
    private String endpoint = "";

    /** 普通 OCR 的鉴权，按 Bearer 发送 */
    private String apiKey = "";

    private int timeoutSeconds = 30;

    /** 文档解析超时单独放宽（整份 PDF 逐页跑版面分析） */
    private int structureTimeoutSeconds = 120;

    /** 文本层少于该字数才触发 OCR */
    private int minChars = 60;

    /** 最多处理页数，避免超大 PDF 拖垮服务 */
    private int maxPages = 5;

    /** 渲染 DPI，越高 OCR 越准但越慢 */
    private int renderDpi = 150;

    /** 正文入库上限（字符） */
    private int maxCacheChars = 20000;

    public boolean structureEnabled() {
        return !"none".equalsIgnoreCase(provider)
                && !"ocr".equalsIgnoreCase(provider)
                && structureEndpoint != null && !structureEndpoint.isBlank();
    }

    public boolean paddleEnabled() {
        return !"none".equalsIgnoreCase(provider)
                && endpoint != null && !endpoint.isBlank();
    }

    public String structureKey() {
        return structureApiKey != null && !structureApiKey.isBlank() ? structureApiKey : apiKey;
    }
}
