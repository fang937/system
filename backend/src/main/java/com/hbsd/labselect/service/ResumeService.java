package com.hbsd.labselect.service;

import com.hbsd.labselect.common.BizException;
import com.hbsd.labselect.common.UserContext;
import com.hbsd.labselect.ocr.ResumeTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生简历：本地落盘 + 元数据入库。
 *
 * <p>约束：一位学生只保留一份当前简历，重新上传即替换（旧文件删除）；
 * 只允许 PDF / DOC / DOCX，大小受 app.upload.max-resume-mb 限制；
 * 文件不通过静态目录暴露，下载一律走鉴权接口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_TYPE = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final JdbcTemplate jdbc;
    private final ResumeTextService resumeTextService;

    @Value("${app.upload.resume-dir:./uploads/resumes}")
    private String resumeDir;

    @Value("${app.upload.max-resume-mb:10}")
    private int maxResumeMb;

    /** 当前学生简历元数据；未上传返回 null 字段而不是报错，方便前端判空。 */
    public Map<String, Object> meta(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, file_name AS fileName, content_type AS contentType, size_bytes AS sizeBytes,
                       uploaded_at AS uploadedAt
                  FROM student_resumes WHERE student_id = ?
                """, studentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploaded", !rows.isEmpty());
        if (rows.isEmpty()) {
            result.put("fileName", null);
            result.put("sizeBytes", null);
            result.put("uploadedAt", null);
            result.put("downloadPath", null);
            return result;
        }
        result.putAll(rows.get(0));
        result.put("downloadPath", "/api/student/resume/file");
        return result;
    }

    @Transactional
    public Map<String, Object> upload(Long studentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的简历文件");
        }
        if (file.getSize() > (long) maxResumeMb * 1024 * 1024) {
            throw new BizException("简历不能超过 " + maxResumeMb + "MB");
        }

        String original = sanitize(file.getOriginalFilename());
        String ext = extension(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持 PDF / DOC / DOCX 格式");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !ALLOWED_TYPE.contains(contentType)) {
            throw new BizException("文件类型不支持，请上传 PDF / DOC / DOCX");
        }

        Path dir = Paths.get(resumeDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("简历目录不可用：" + e.getMessage());
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String storedName = studentId + "-" + stamp + "-" + Integer.toHexString((int) (Math.random() * 0xffff)) + "." + ext;
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) {
            throw new BizException("文件名不合法");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException("简历保存失败：" + e.getMessage());
        }

        // 替换旧文件，同时让旧的正文缓存失效
        List<Map<String, Object>> old = jdbc.queryForList(
                "SELECT stored_name AS storedName FROM student_resumes WHERE student_id = ?", studentId);
        if (!old.isEmpty()) {
            deleteQuietly(dir.resolve(String.valueOf(old.get(0).get("storedName"))).normalize());
            resumeTextService.invalidate(studentId);
        }

        jdbc.update("""
                INSERT INTO student_resumes (student_id, file_name, stored_name, content_type, size_bytes)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE file_name = VALUES(file_name), stored_name = VALUES(stored_name),
                    content_type = VALUES(content_type), size_bytes = VALUES(size_bytes), uploaded_at = NOW()
                """, studentId, original, storedName, contentType, file.getSize());
        jdbc.update("UPDATE student_profiles SET resume_url = ? WHERE user_id = ?", storedName, studentId);

        log.info("学生 {} 上传简历：{}（{} 字节）", studentId, original, file.getSize());
        return meta(studentId);
    }

    @Transactional
    public void delete(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT stored_name AS storedName FROM student_resumes WHERE student_id = ?", studentId);
        if (rows.isEmpty()) {
            return;
        }
        Path dir = Paths.get(resumeDir).toAbsolutePath().normalize();
        deleteQuietly(dir.resolve(String.valueOf(rows.get(0).get("storedName"))).normalize());
        resumeTextService.invalidate(studentId);
        jdbc.update("DELETE FROM student_resumes WHERE student_id = ?", studentId);
        jdbc.update("UPDATE student_profiles SET resume_url = NULL WHERE user_id = ?", studentId);
    }

    /** 学生本人下载。 */
    public ResumeFile downloadForStudent(Long studentId) {
        return load(studentId);
    }

    /** 导师下载：仅限本轮报考自己的学生。 */
    public ResumeFile downloadForTutor(Long applicationId) {
        Long userId = UserContext.get().id();
        List<Map<String, Object>> tutors = jdbc.queryForList("SELECT id FROM tutors WHERE user_id = ?", userId);
        if (tutors.isEmpty()) {
            throw new BizException(403, "当前账号不是导师");
        }
        Long tutorId = ((Number) tutors.get(0).get("id")).longValue();
        List<Map<String, Object>> apps = jdbc.queryForList("""
                SELECT student_id AS studentId, tutor_1_id AS t1, tutor_2_id AS t2, tutor_3_id AS t3
                  FROM applications WHERE id = ?
                """, applicationId);
        if (apps.isEmpty()) {
            throw new BizException("志愿记录不存在");
        }
        Map<String, Object> app = apps.get(0);
        boolean mine = tutorId.equals(asLong(app.get("t1")))
                || tutorId.equals(asLong(app.get("t2")))
                || tutorId.equals(asLong(app.get("t3")));
        if (!mine) {
            throw new BizException(403, "该学生未报考你，无法查看简历");
        }
        return load(asLong(app.get("studentId")));
    }

    private ResumeFile load(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT file_name AS fileName, stored_name AS storedName, content_type AS contentType
                  FROM student_resumes WHERE student_id = ?
                """, studentId);
        if (rows.isEmpty()) {
            throw new BizException(404, "该学生尚未上传简历");
        }
        Map<String, Object> row = rows.get(0);
        Path dir = Paths.get(resumeDir).toAbsolutePath().normalize();
        Path path = dir.resolve(String.valueOf(row.get("storedName"))).normalize();
        if (!path.startsWith(dir) || !Files.exists(path)) {
            throw new BizException(404, "简历文件不存在，请重新上传");
        }
        Resource resource = new FileSystemResource(path);
        return new ResumeFile(resource, String.valueOf(row.get("fileName")),
                String.valueOf(row.get("contentType")));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("旧简历删除失败：{}", e.getMessage());
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "resume.pdf";
        }
        String base = name.replaceAll(".*[\\\\/]", "").trim();
        return base.isEmpty() ? "resume.pdf" : base;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    /** 下载所需的三要素 */
    public record ResumeFile(Resource resource, String fileName, String contentType) {
    }
}
