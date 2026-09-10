package com.iflytek.interview.file.util;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件校验：类型白名单 / 大小上限 / 文件名安全
 */
@Setter
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileValidator {

    private List<String> allowedTypes;

    private long maxSize;

    public void validate(MultipartFile file) {
        // 1. 大小
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件超过大小限制");
        }
        // 2. 类型（白名单；contentType 由浏览器填写，可被伪造，服务端按需再验魔数）
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文件类型");
        }
        // 3. 文件名安全（防路径穿越 ../ 等）
        String name = file.getOriginalFilename();
        if (name == null || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法文件名");
        }
    }
}
