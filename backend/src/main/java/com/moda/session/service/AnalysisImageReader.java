package com.moda.session.service;

import com.moda.ai.AnalysisImage;
import com.moda.common.enums.AnalysisType;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 사진 검증 후 메모리 바이트로 읽습니다 (docs/api-spec.md 1.2, R-01).
 * <ul>
 *   <li>파트 누락·빈 파일 → {@code 400 IMAGE_REQUIRED}</li>
 *   <li>10MB 초과 → {@code 413 IMAGE_TOO_LARGE}</li>
 *   <li>{@code image/jpeg}·{@code image/png} 외 → {@code 415 UNSUPPORTED_IMAGE_TYPE}</li>
 * </ul>
 * 디스크에 쓰지 않으며, 원본 파일명은 AI 서버로 전달하지 않습니다.
 */
@Component
public class AnalysisImageReader {

    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    private final long maxBytes;

    public AnalysisImageReader(@Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
        this.maxBytes = maxFileSize.toBytes();
    }

    public AnalysisImage read(MultipartFile file, AnalysisType type) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_REQUIRED);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        String contentType = normalize(file.getContentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        try {
            byte[] bytes = file.getBytes();
            return new AnalysisImage(bytes, contentType, type.name().toLowerCase(Locale.ROOT) + "." + extension,
                    UUID.randomUUID().toString());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_REQUIRED, ErrorCode.IMAGE_REQUIRED.defaultMessage(), e);
        }
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip().toLowerCase(Locale.ROOT);
    }
}
