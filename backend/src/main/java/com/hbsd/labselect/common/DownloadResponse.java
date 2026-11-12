package com.hbsd.labselect.common;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** 文件下载响应的统一装配：文件名按 UTF-8 编码，浏览器保存时不会乱码。 */
public final class DownloadResponse {

    private DownloadResponse() {
    }

    public static ResponseEntity<Resource> of(Resource resource, String fileName, String contentType) {
        MediaType mediaType;
        try {
            mediaType = contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName == null ? "download" : fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    /**
     * 文件接口的错误必须用真实 HTTP 状态 + JSON 体返回：
     * 否则前端在 blob 模式下会把错误 JSON 当成文件保存下来。
     */
    public static ResponseEntity<?> error(int code, String message) {
        int status = code >= 400 && code <= 599 ? code : 400;
        return ResponseEntity.status(status).body(R.fail(code, message));
    }
}
