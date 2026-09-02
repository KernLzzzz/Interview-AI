-- ==========================================================
-- 面试平台初始化脚本 init.sql
-- 内容：创建数据库 + 4 张业务表 + 测试数据
-- 执行方式（任选其一）：
--   ① 客户端（Navicat / DataGrip）：打开本文件 → 直接运行
--   ② 命令行：mysql -u root -p < sql/init.sql
-- 注意：本课脚本约定"首次执行"，若重复执行建议加 IF NOT EXISTS
-- ==========================================================

-- ========== 一、创建数据库 ==========
CREATE DATABASE `interview_ai`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `interview_ai`;

-- ========== 二、用户表 sys_user ==========
CREATE TABLE `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密存储）',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `role` VARCHAR(20) DEFAULT 'candidate' COMMENT '角色：candidate-候选人 interviewer-面试官 admin-管理员',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (`username`),
    INDEX idx_email (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ========== 三、面试场景表 interview_scenario ==========
CREATE TABLE `interview_scenario` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '场景ID',
    `name` VARCHAR(100) NOT NULL COMMENT '场景名称（如：Java后端工程师）',
    `description` TEXT COMMENT '场景描述',
    `tech_field` VARCHAR(50) NOT NULL COMMENT '技术领域：AI/大数据/物联网',
    `difficulty` TINYINT DEFAULT 1 COMMENT '难度：1-初级 2-中级 3-高级',
    `cover_image` VARCHAR(255) COMMENT '封面图',
    `question_count` INT DEFAULT 0 COMMENT '题目数量（冗余字段，提升查询性能）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_sort (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试场景表';

-- ========== 四、面试题目表 interview_question ==========
CREATE TABLE `interview_question` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '题目ID',
    `scenario_id` BIGINT NOT NULL COMMENT '所属场景ID',
    `content` TEXT NOT NULL COMMENT '题目内容',
    `type` VARCHAR(20) DEFAULT 'technical' COMMENT '类型：technical-技术 behavioral-行为 hr-HR面',
    `difficulty` TINYINT DEFAULT 1 COMMENT '难度：1-简单 2-中等 3-困难',
    `expected_answer` TEXT COMMENT '参考答案',
    `keywords` VARCHAR(500) COMMENT '关键词（用于答案评分）',
    `sort_order` INT DEFAULT 0 COMMENT '题目顺序',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenario (`scenario_id`),
    INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试题目表';

-- ========== 五、面试记录表 interview_record ==========
CREATE TABLE `interview_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `scenario_id` BIGINT NOT NULL COMMENT '场景ID',
    `question_data` TEXT COMMENT '抽到的题目快照（JSON：[{id,content,difficulty}]）',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待开始 ongoing-进行中 completed-已完成 cancelled-已取消',
    `score` DECIMAL(5,2) COMMENT '总分',
    `duration` INT COMMENT '面试时长（秒）',
    `answer_data` TEXT COMMENT '答题数据（JSON格式字符串）',
    `ai_feedback` TEXT COMMENT 'AI评价反馈',
    `started_at` DATETIME COMMENT '开始时间',
    `completed_at` DATETIME COMMENT '完成时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (`user_id`),
    INDEX idx_scenario (`scenario_id`),
    INDEX idx_created (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录表';

-- ========== 六、文件记录表 file_record ==========
CREATE TABLE `file_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '上传用户ID',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `object_name` VARCHAR(500) NOT NULL COMMENT 'MinIO 对象路径',
    `url` VARCHAR(500) COMMENT '访问 URL',
    `file_size` BIGINT COMMENT '文件大小（字节）',
    `content_type` VARCHAR(100) COMMENT '文件类型',
    `module` VARCHAR(50) COMMENT '模块：resume/avatar/video/attachment',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    INDEX idx_user (`user_id`),
    INDEX idx_module (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件记录表';

-- ========== 七、异步任务日志表 task_log ==========
CREATE TABLE `task_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_type` VARCHAR(50) NOT NULL COMMENT '任务类型：report_generate 等',
    `biz_id` BIGINT COMMENT '业务ID（如面试记录ID）',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/running/success/failed',
    `error_message` TEXT COMMENT '失败原因',
    `retry_count` INT DEFAULT 0 COMMENT '已重试次数',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (`status`),
    INDEX idx_biz (`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务日志表';

-- ========== 八、测试数据 ==========
-- 测试账号（密码统一为 123456，BCrypt 加密存储；角色关联见 rbac.sql 第八节）
INSERT INTO `sys_user` (`username`, `password`, `email`, `nickname`, `role`)
VALUES
('admin',       '$2a$10$E/IWSnPMvVf0VVCOOHa9EuXDck26k3Z2oGwTF3cYFk8SV6TnPLPY.', 'admin@example.com',       '管理员',   'admin'),
('test01',      '$2a$10$E/IWSnPMvVf0VVCOOHa9EuXDck26k3Z2oGwTF3cYFk8SV6TnPLPY.', 'test01@example.com',      '测试用户1', 'candidate'),
('interviewer', '$2a$10$E/IWSnPMvVf0VVCOOHa9EuXDck26k3Z2oGwTF3cYFk8SV6TnPLPY.', 'interviewer@example.com', '面试官',   'interviewer');

-- 面试场景
INSERT INTO `interview_scenario` (`name`, `description`, `tech_field`, `difficulty`, `question_count`)
VALUES
('Java后端工程师', '考察Java基础、Spring框架、数据库等', '后端', 2, 0),
('前端工程师',     '考察HTML/CSS、JavaScript、Vue/React等', '前端', 2, 0),
('测试工程师',     '考察测试理论、自动化测试、性能测试等', '测试', 1, 0);

-- 面试题目（示例）
INSERT INTO `interview_question` (`scenario_id`, `content`, `type`, `difficulty`, `expected_answer`)
VALUES
(1, '请介绍一下Java中的集合框架，ArrayList和LinkedList的区别是什么？', 'technical', 1, 'ArrayList基于数组实现，随机访问快；LinkedList基于链表实现，插入删除快。'),
(1, 'Spring Boot的自动配置原理是什么？', 'technical', 2, '通过@EnableAutoConfiguration注解，扫描classpath下的META-INF/spring.factories文件...');

-- ==========================================================
-- 后续讲次扩展：第8讲的角色/权限表单独放在 rbac.sql（本目录）
-- 两者共用 interview_ai 库，先运行 init.sql 再运行 rbac.sql
-- ==========================================================
