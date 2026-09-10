package com.iflytek.interview.file.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iflytek.interview.file.entity.FileRecord;
import com.iflytek.interview.file.util.MinioUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 孤儿媒体清理：删除「已上传但从未被任何面试记录引用」的录制文件。
 *
 * <p>这类文件有四个来源：上传成功但提交失败/放弃、text 模式误传、面试被取消、
 * 提交失败后重试导致的重复上传。它们既不会被回看，也不会被评测，只会持续占用对象存储。
 *
 * <p>判定采用 TTL 兜底：超过 ttl 仍无引用即视为孤儿。ttl 必须远大于正常流程耗时
 * （上传到提交是秒级），默认 24 小时——即使用户上传后离开再回来提交，也不会被误删。
 */
@Slf4j
@Component
public class OrphanMediaCleaner {

    /** 只清理面试媒体，不碰简历等其他模块的文件 */
    private static final String MEDIA_MODULE_PREFIX = "interview-";

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private FileRecordService fileRecordService;

    /**
     * 清理一轮孤儿媒体。
     *
     * @param ttl        超过该时长仍未被引用才视为孤儿
     * @param batchLimit 单次最多处理条数，避免一次性删除过多拖长任务
     * @return 实际清理成功的条数
     */
    public int clean(Duration ttl, int batchLimit) {
        LocalDateTime deadline = LocalDateTime.now().minus(ttl);
        QueryWrapper<FileRecord> query = new QueryWrapper<>();
        query.likeRight("module", MEDIA_MODULE_PREFIX)
                .lt("created_at", deadline)
                .notInSql("id", "SELECT media_file_id FROM interview_record WHERE media_file_id IS NOT NULL")
                .orderByAsc("id")
                .last("LIMIT " + batchLimit);

        List<FileRecord> orphans = fileRecordService.list(query);
        if (orphans.isEmpty()) {
            return 0;
        }

        int cleaned = 0;
        for (FileRecord orphan : orphans) {
            try {
                // 先删对象、再删记录：中途失败时记录仍在，下一轮可重试；
                // 若反过来，对象会失去索引，变成永远无从追溯的真垃圾。
                minioUtil.delete(orphan.getObjectName());
                fileRecordService.removeById(orphan.getId());
                cleaned++;
                log.info("已清理孤儿文件: id={}, object={}", orphan.getId(), orphan.getObjectName());
            } catch (Exception e) {
                // 单条失败不影响其余，留到下一轮重试
                log.warn("孤儿文件清理失败，跳过待下轮重试: object={}, 原因={}",
                        orphan.getObjectName(), e.getMessage());
            }
        }
        return cleaned;
    }
}
