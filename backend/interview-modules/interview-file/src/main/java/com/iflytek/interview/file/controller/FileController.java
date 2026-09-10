package com.iflytek.interview.file.controller;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.file.entity.FileRecord;
import com.iflytek.interview.file.service.FileRecordService;
import com.iflytek.interview.file.util.FileValidator;
import com.iflytek.interview.file.util.MinioUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private FileValidator fileValidator;

    @Autowired
    private FileRecordService fileRecordService;

    /**
     * 上传文件
     * POST /api/files/upload?module=resume
     * 参数 file：MultipartFile
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam String module,
            @RequestPart("file") MultipartFile file) {

        // 1. 登录用户（文件归属到人）
        Long userId = SecurityUtil.getCurrentUserId();

        if (module == null || !module.matches("[a-z0-9-]{1,40}")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法文件模块");
        }

        // 2. 业务层安检：类型白名单 / 大小上限 / 文件名安全
        fileValidator.validate(file);

        // 3. 生成唯一对象路径：{module}/{userId}/{uuid}_{originalName}
        String originalName = file.getOriginalFilename();
        String objectName = module + "/" + userId + "/"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "_" + originalName;

        // 4. 上传到 MinIO（读取文件流的受检异常转为业务异常，统一由全局异常处理器兜底）
        try {
            minioUtil.upload(objectName, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 5. 记录元数据
        String url = minioUtil.getUrl(objectName);
        FileRecord record = new FileRecord();
        record.setUserId(userId);
        record.setOriginalName(originalName);
        record.setObjectName(objectName);
        record.setUrl(url);
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setModule(module);
        fileRecordService.save(record);

        // 6. 返回
        return Result.success(Map.of("fileId", record.getId(), "url", url, "originalName", originalName));
    }
}
