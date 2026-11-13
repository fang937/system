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
     * auto：MinerU → PP-StructureV3 → 普通 OCR 依次尝试；
     * mineru：只用 MinerU；pp-structure-v3：只用文档解析；ocr：只用普通识别；none：全部禁用。
     * 该值可被管理端「简历解析引擎」覆盖（存 system_config: ocr.provider）。
     */
    private String provider = "auto";

    /** MinerU 精准解析接口根地址 */
    private String mineruEndpoint = "https://mineru.net/api/v4";

    /** MinerU 访问令牌（Bearer） */
    private String mineruToken = "";

    /** MinerU 解析模型：pipeline / vlm（vlm 对复杂版式更准，速度略慢） */
    private String mineruModel = "vlm";

    /** MinerU 是否按扫描件强制 OCR */
    private boolean mineruForceOcr = true;

    /** MinerU 任务轮询超时（整份 PDF 解析 + 排队） */
    private int mineruTimeoutSeconds = 180;

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

    /** MinerU 是否具备调用条件（只看令牌，provider 由 OcrProviderResolver 决定） */
    public boolean mineruConfigured() {
        return mineruToken != null && !mineruToken.isBlank()
                && mineruEndpoint != null && !mineruEndpoint.isBlank();
    }

    public boolean paddleEnabled() {
        return !"none".equalsIgnoreCase(provider)
                && endpoint != null && !endpoint.isBlank();
    }

    public String structureKey() {
        return structureApiKey != null && !structureApiKey.isBlank() ? structureApiKey : apiKey;
    }
}
