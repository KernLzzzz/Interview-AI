-- ==========================================================
-- 面试平台初始化脚本 init.sql
-- 内容：创建数据库、核心业务表与可直接体验的种子数据
-- 执行方式（任选其一）：
--   ① 客户端（Navicat / DataGrip）：打开本文件 → 直接运行
--   ② 命令行：mysql -u root -p < sql/init.sql
-- 建议在全新实例执行；已有数据库请使用对应 migration 脚本
-- ==========================================================

-- ========== 一、创建数据库 ==========
CREATE DATABASE IF NOT EXISTS `interview_ai`
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
    `question_data` JSON COMMENT '不含答案的抽题快照',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待开始 ongoing-进行中 completed-已完成 cancelled-已取消',
    `score` DECIMAL(5,2) COMMENT '总分',
    `duration` INT COMMENT '面试时长（秒）',
    `answer_data` JSON COMMENT '服务端校验并重建的结构化答题数据',
    `ai_feedback` JSON COMMENT '结构化 AI 评价反馈',
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
    `task_type` VARCHAR(50) NOT NULL COMMENT '任务类型：ai_evaluation',
    `biz_id` BIGINT COMMENT '业务ID（如面试记录ID）',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/running/success/failed',
    `error_message` TEXT COMMENT '失败原因',
    `retry_count` INT DEFAULT 0 COMMENT '已重试次数',
    `max_retries` INT DEFAULT 3 COMMENT '最大重试次数',
    `callback_url` VARCHAR(500) COMMENT '可选业务回调地址',
    `result_payload` JSON COMMENT '结构化评测结果',
    `next_retry_at` DATETIME COMMENT '下次允许重试时间',
    `started_at` DATETIME COMMENT '最近一次开始执行时间',
    `finished_at` DATETIME COMMENT '最终完成时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (`status`, `next_retry_at`),
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
('Java后端工程师', '考察Java基础、Spring框架、数据库等', '后端', 2, 5),
('前端工程师',     '考察HTML/CSS、JavaScript、Vue/React等', '前端', 2, 5),
('测试工程师',     '考察测试理论、自动化测试、性能测试等', '测试', 1, 5);

-- 面试题目（示例）
INSERT INTO `interview_question` (`scenario_id`, `content`, `type`, `difficulty`, `expected_answer`, `keywords`)
VALUES
(1, '请介绍 Java 集合框架，ArrayList 和 LinkedList 的主要区别是什么？', 'technical', 1, 'ArrayList 基于动态数组，随机访问快；LinkedList 基于双向链表，已定位节点的插入删除更直接。', 'ArrayList,LinkedList,数组,链表'),
(1, 'Spring Boot 自动配置的工作原理是什么？', 'technical', 2, '通过自动配置导入、条件注解和配置元数据，根据类路径与 Bean 状态装配组件。', '自动配置,条件注解,类路径,Bean'),
(1, '如何设计 Redis 与 MySQL 的缓存一致性方案？', 'technical', 2, '常用 Cache Aside：先更新数据库再删除缓存，并结合重试、延迟双删或订阅 binlog 保证最终一致。', 'Cache Aside,删除缓存,重试,最终一致'),
(1, '请解释 JVM 垃圾回收中的可达性分析与常见收集器。', 'technical', 3, '从 GC Roots 做可达性分析，并按延迟与吞吐目标选择 G1、ZGC 等收集器。', 'GC Roots,可达性分析,G1,ZGC'),
(1, '讲述一次你定位线上性能问题的经历。', 'behavioral', 2, '使用 STAR 结构说明现象、指标、定位链路、修复方案和量化结果。', '指标,定位,修复,结果'),
(2, '浏览器从输入 URL 到页面可交互经历了哪些过程？', 'technical', 1, '包括 DNS、连接建立、HTTP 请求、HTML 解析、资源加载、渲染与脚本执行。', 'DNS,HTTP,解析,渲染'),
(2, 'Vue 3 Composition API 如何组织可复用状态逻辑？', 'technical', 2, '将响应式状态、计算属性和副作用封装到 composable，并明确输入、输出与生命周期。', 'Composition API,composable,响应式,生命周期'),
(2, '如何减少首屏资源体积并改善 Core Web Vitals？', 'technical', 2, '使用路由懒加载、代码分割、图片优化、关键资源预加载，并围绕 LCP、INP、CLS 持续测量。', '懒加载,代码分割,LCP,INP,CLS'),
(2, '解释事件循环中宏任务、微任务与渲染时机。', 'technical', 3, '每轮事件循环执行一个宏任务，清空微任务队列，再按浏览器调度进入渲染阶段。', '事件循环,宏任务,微任务,渲染'),
(2, '需求频繁变化时，你如何保证前端交付质量？', 'behavioral', 2, '澄清验收标准，拆分稳定边界，补充自动化测试，并透明同步风险和取舍。', '验收标准,边界,自动化测试,风险'),
(3, '什么是测试金字塔，各层测试的职责是什么？', 'technical', 1, '以大量单元测试为基础，配合适量服务集成测试与少量端到端测试。', '单元测试,集成测试,端到端,测试金字塔'),
(3, '如何为登录接口设计覆盖完整的测试用例？', 'technical', 1, '覆盖正常、边界、异常、安全、并发与会话生命周期，并验证状态码和响应内容。', '边界,异常,安全,并发,会话'),
(3, 'JMeter 性能测试中如何区分响应时间升高的原因？', 'technical', 2, '结合吞吐、分位耗时、错误率和服务端 CPU、线程池、GC、SQL/APM 指标定位瓶颈。', '吞吐,分位耗时,错误率,线程池,SQL'),
(3, '如何验证一个异步任务系统不会丢任务？', 'technical', 3, '先持久化任务，使用可观测状态机与安全拒绝策略，模拟进程退出并验证重试恢复和幂等。', '持久化,拒绝策略,重试,幂等'),
(3, '描述一次你发现高风险缺陷并推动修复的经历。', 'behavioral', 2, '说明风险证据、影响范围、复现方式、协作决策以及回归结果。', '风险,影响范围,复现,回归');

-- ==========================================================
-- 角色与权限表单独放在 rbac.sql（本目录）
-- 两者共用 interview_ai 库，先运行 init.sql 再运行 rbac.sql
-- ==========================================================
