package com.hbsd.labselect.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OCR 配置绑定与客户端装配。 */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {

    @Bean
    public PpStructureV3Client ppStructureV3Client(OcrProperties properties, ObjectMapper objectMapper) {
        return new PpStructureV3Client(properties, objectMapper);
    }

    @Bean
    public PaddleOcrClient paddleOcrClient(OcrProperties properties, ObjectMapper objectMapper) {
        return new PaddleOcrClient(properties, objectMapper);
    }
}
