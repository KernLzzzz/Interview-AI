package com.iflytek.interview.file.util;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.file.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 操作工具类：上传 / 删除 / 预签名URL
 */
@Slf4j
@Component
public class MinioUtil {

    @Autowired
    private MinioProperties minioProperties;

    private MinioClient minioClient;

    // 单文件最大分片（MinIO SDK 要求 stream 指定 partSize）
    private static final long PART_SIZE = 10 * 1024 * 1024;  // 10MB

    // 桶是否已确认可用；MinIO 可能晚于本服务就绪，故采用懒确认而非一次性初始化
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        // 桶不存在则创建；此处失败不阻断启动（compose 里 minio 只保证 service_started），
        // 延后到首次上传时通过 ensureBucket() 重试
        try {
            ensureBucket();
        } catch (Exception e) {
            log.warn("MinIO 暂不可用，将在首次使用时重试: {}", rootCause(e));
        }
    }

    /**
     * 确认桶存在，不存在则创建。成功一次后短路。
     */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        try {
            String bucket = minioProperties.getBucket();
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket).build());
                log.info("MinIO 已创建桶: {}", bucket);
            }
            bucketReady.set(true);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "MinIO 桶不可用[" + minioProperties.getBucket() + "@" + minioProperties.getEndpoint() + "]",
                    e);
        }
    }

    /**
     * 取最内层异常原因，用于把失败原因透出到调用方，避免只剩一句「文件上传失败」无法定位。
     */
    private static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String msg = c.getMessage();
        return c.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    /**
     * 上传文件
     * @param objectName  对象路径，如 resumes/1/uuid_简历.pdf
     * @param inputStream 文件流
     * @param contentType 文件类型，如 application/pdf
     */
    public void upload(String objectName, InputStream inputStream, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .stream(inputStream, -1, PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            String reason = rootCause(e);
            log.error("文件上传失败: {} -> {}", objectName, reason, e);
            // 保留根因：否则前端只能看到「文件上传失败」，无法判断是连不上、桶不存在还是凭证错
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文件上传失败（" + reason + "）");
        }
    }

    /**
     * 删除对象
     */
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            String reason = rootCause(e);
            log.error("文件删除失败: {} -> {}", objectName, reason, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED, "文件删除失败（" + reason + "）");
        }
    }

    /**
     * 获取对象访问 URL（不带签名，需要桶是公开读；私有桶用预签名URL）
     */
    public String getUrl(String objectName) {
        return minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/" + objectName;
    }

    /**
     * 获取预签名下载 URL（私有桶时给临时凭证，expiry 秒后失效）
     */
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            String reason = rootCause(e);
            log.error("获取文件URL失败: {} -> {}", objectName, reason, e);
            throw new BusinessException(ErrorCode.FILE_URL_FAILED, "获取文件地址失败（" + reason + "）");
        }
    }
}