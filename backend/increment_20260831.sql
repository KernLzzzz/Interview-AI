-- ============================================================
-- SQL 增量：面试题快照字段（2026-08-31）
-- 适用对象：已按旧版 init.sql 建库的库；新库直接跑完整 init.sql 无需本文件
-- ============================================================

-- 1. interview_record 增加题目快照列：开始面试时按难度比例 3:5:2 抽题，快照存此处
ALTER TABLE `interview_record`
    ADD COLUMN `question_data` TEXT COMMENT '抽到的题目快照（JSON：[{id,content,difficulty}]）' AFTER `scenario_id`;
