USE `interview_ai`;

-- 已有数据库升级到 Redis Hash 题库缓存 + 可追踪异步评测任务结构。
ALTER TABLE `task_log`
    ADD COLUMN `max_retries` INT DEFAULT 3 COMMENT '最大重试次数' AFTER `retry_count`,
    ADD COLUMN `callback_url` VARCHAR(500) COMMENT '可选业务回调地址' AFTER `max_retries`,
    ADD COLUMN `result_payload` JSON COMMENT '结构化评测结果' AFTER `callback_url`,
    ADD COLUMN `next_retry_at` DATETIME COMMENT '下次允许重试时间' AFTER `result_payload`,
    ADD COLUMN `started_at` DATETIME COMMENT '最近一次开始执行时间' AFTER `next_retry_at`,
    ADD COLUMN `finished_at` DATETIME COMMENT '最终完成时间' AFTER `started_at`,
    ADD INDEX `idx_status_retry` (`status`, `next_retry_at`);

-- 执行前请确保历史列内容均为合法 JSON。
ALTER TABLE `interview_record`
    ADD COLUMN `interview_mode` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '面试模式：text/voice/video' AFTER `scenario_id`,
    ADD COLUMN `media_file_id` BIGINT COMMENT '录音/录像文件记录ID' AFTER `duration`,
    ADD COLUMN `media_duration` INT COMMENT '媒体时长（秒）' AFTER `media_file_id`,
    ADD INDEX `idx_media_file` (`media_file_id`),
    MODIFY COLUMN `question_data` JSON COMMENT '不含答案的抽题快照',
    MODIFY COLUMN `answer_data` JSON COMMENT '服务端校验并重建的结构化答题数据',
    MODIFY COLUMN `ai_feedback` JSON COMMENT '结构化 AI 评价反馈';
