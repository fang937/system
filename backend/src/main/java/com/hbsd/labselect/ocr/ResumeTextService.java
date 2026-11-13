package com.hbsd.labselect.ocr;

import com.hbsd.labselect.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 简历正文抽取：PDF 文本层 → （必要时）PaddleOCR → DOCX 直读，结果按简历缓存。
 *
 * <p>为什么先文本层：绝大多数简历是导出型 PDF，文本层准确、零依赖、毫秒级；
 * 只有扫描件/图片版才值得走 OCR（渲染 + 识别，秒级且吃资源）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeTextService {

    private final JdbcTemplate jdbc;
    private final OcrProperties properties;
    private final PaddleOcrClient ocrClient;
    private final PpStructureV3Client structureClient;
    private final MineruClient mineruClient;
    private final OcrProviderResolver providerResolver;

    @Value("${app.upload.resume-dir:./uploads/resumes}")
    private String resumeDir;

    /** 学生当前简历的正文；无简历或无法解析时返回空文本 + 原因引擎标识。 */
    public Extract extract(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, stored_name AS storedName, file_name AS fileName
                  FROM student_resumes WHERE student_id = ?
                """, studentId);
        if (rows.isEmpty()) {
            return Extract.empty("none");
        }
        Map<String, Object> row = rows.get(0);
        Long resumeId = ((Number) row.get("id")).longValue();
        return extract(resumeId, String.valueOf(row.get("storedName")), String.valueOf(row.get("fileName")));
    }

    public Extract extract(Long resumeId, String storedName, String fileName) {
        Extract cached = readCache(resumeId);
        if (cached != null) {
            return cached;
        }
        Path dir = Paths.get(resumeDir).toAbsolutePath().normalize();
        Path path = dir.resolve(storedName).normalize();
        if (!path.startsWith(dir) || !Files.exists(path)) {
            return Extract.empty("missing");
        }

        String ext = extension(fileName != null ? fileName : storedName);
        Extract result;
        try {
            result = switch (ext) {
                case "pdf" -> fromPdf(path);
                case "docx" -> fromDocx(path);
                default -> Extract.empty("unsupported");
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("简历解析失败 {}：{}", storedName, e.getMessage());
            return Extract.empty("failed");
        }

        if (result.chars() > 0) {
            writeCache(resumeId, result);
        }
        return result;
    }

    /** 简历被替换/删除时清理缓存。 */
    public void invalidate(Long studentId) {
        jdbc.update("""
                DELETE rt FROM resume_texts rt
                  JOIN student_resumes sr ON sr.id = rt.resume_id
                 WHERE sr.student_id = ?
                """, studentId);
    }

    /* ---------------- PDF ---------------- */

    private Extract fromPdf(Path path) throws Exception {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            int pages = document.getNumberOfPages();
            String text = normalize(new PDFTextStripper().getText(document));

            if (text.length() >= properties.getMinChars()) {
                return new Extract(text, "pdf-text", pages, text.length(), false);
            }

            // 按管理端选择的引擎顺序依次尝试：auto 时 MinerU → PP-StructureV3 → 普通 OCR
            for (String engine : providerResolver.plan()) {
                if ("mineru".equals(engine) && properties.mineruConfigured()) {
                    try {
                        String markdown = cleanMarkdown(mineruClient.parse(path));
                        if (markdown.length() > text.length()) {
                            return new Extract(markdown, "mineru", pages, markdown.length(), false);
                        }
                    } catch (RuntimeException e) {
                        log.warn("MinerU 解析失败，尝试降级：{}", e.getMessage());
                    }
                }
                if ("pp-structure-v3".equals(engine) && structureAvailable()) {
                    try {
                        PpStructureV3Client.ParseResult parsed = structureClient.parsePdf(path);
                        String markdown = cleanMarkdown(parsed.markdown());
                        if (markdown.length() > text.length()) {
                            return new Extract(markdown, "pp-structure-v3", parsed.pages(), markdown.length(), false);
                        }
                    } catch (RuntimeException e) {
                        log.warn("PP-StructureV3 解析失败，尝试降级：{}", e.getMessage());
                    }
                }
                if ("ocr".equals(engine) && paddleAvailable()) {
                    try {
                        List<byte[]> images = render(document);
                        String ocrText = normalize(ocrClient.recognize(images));
                        if (ocrText.length() > text.length()) {
                            return new Extract(ocrText, "paddleocr", Math.min(pages, properties.getMaxPages()),
                                    ocrText.length(), false);
                        }
                    } catch (RuntimeException e) {
                        log.warn("普通 OCR 解析失败：{}", e.getMessage());
                    }
                }
            }
            return new Extract(text, "pdf-text", pages, text.length(), false);
        } catch (InvalidPasswordException e) {
            return Extract.empty("encrypted");
        }
    }

    private boolean structureAvailable() {
        return properties.getStructureEndpoint() != null && !properties.getStructureEndpoint().isBlank();
    }

    private boolean paddleAvailable() {
        return properties.getEndpoint() != null && !properties.getEndpoint().isBlank();
    }

    private List<byte[]> render(PDDocument document) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        int pages = Math.min(document.getNumberOfPages(), Math.max(1, properties.getMaxPages()));
        List<byte[]> images = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, properties.getRenderDpi());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            images.add(out.toByteArray());
        }
        return images;
    }

    /* ---------------- DOCX ---------------- */

    private Extract fromDocx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (!paragraph.getText().isBlank()) {
                    sb.append(paragraph.getText()).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText());
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                }
            }
            String text = normalize(sb.toString());
            return new Extract(text, "docx-text", 0, text.length(), false);
        }
    }

    /* ---------------- 缓存 ---------------- */

    private Extract readCache(Long resumeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT text, engine, pages, chars FROM resume_texts WHERE resume_id = ?
                """, resumeId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String text = row.get("text") == null ? "" : String.valueOf(row.get("text"));
        return new Extract(text, String.valueOf(row.get("engine")),
                intOf(row.get("pages")), intOf(row.get("chars")), true);
    }

    private void writeCache(Long resumeId, Extract extract) {
        String text = extract.text();
        if (text.length() > properties.getMaxCacheChars()) {
            text = text.substring(0, properties.getMaxCacheChars());
        }
        jdbc.update("""
                INSERT INTO resume_texts (resume_id, engine, pages, chars, text)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE engine = VALUES(engine), pages = VALUES(pages),
                    chars = VALUES(chars), text = VALUES(text)
                """, resumeId, extract.engine(), extract.pages(), extract.chars(), text);
    }

    /* ---------------- 工具 ---------------- */

    /**
     * PP-StructureV3 产出的是 Markdown，这里只去掉图片占位与多余空行：
     * 标题、列表、表格的原始结构保留，模型读起来比一整串文本更清楚。
     */
    private String cleanMarkdown(String markdown) {
        if (markdown == null) {
            return "";
        }
        String cleaned = markdown
                .replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (cleaned.length() > properties.getMaxCacheChars()) {
            cleaned = cleaned.substring(0, properties.getMaxCacheChars());
        }
        return cleaned;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** 抽取结果：text 可能为空，engine 说明原因（none/missing/unsupported/encrypted/failed）。 */
    public record Extract(String text, String engine, int pages, int chars, boolean cached) {

        public static Extract empty(String engine) {
            return new Extract("", engine, 0, 0, false);
        }

        public boolean hasText() {
            return chars > 0 && !text.isBlank();
        }
    }
}
