-- ==========================================================
-- 面试平台 RBAC 权限初始化脚本 rbac.sql
-- 前置：先执行 init.sql（保证 interview_ai 库与 sys_user 表存在）
-- 内容：RBAC 四张表（user_role / role / permission / role_permission）
--        + 三角色 + 全部权限 + 关联数据
-- 执行方式：客户端打开直接运行 / mysql -u root -p < rbac.sql
-- ==========================================================

SET NAMES utf8mb4;

USE `interview_ai`;

-- ========== 一、用户-角色关联表 ==========
CREATE TABLE `user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY uk_user_role (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- ========== 二、角色表 ==========
CREATE TABLE `role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称（管理员）',
    `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码（admin）',
    `description` VARCHAR(255) COMMENT '角色描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ========== 三、权限表 ==========
CREATE TABLE `permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    `name` VARCHAR(50) NOT NULL COMMENT '权限名称（新增题目）',
    `code` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码（question:create）',
    `type` VARCHAR(20) DEFAULT 'api' COMMENT '类型：menu-菜单 button-按钮 api-接口',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ========== 四、角色-权限关联表 ==========
CREATE TABLE `role_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY uk_role_perm (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- ========== 五、初始数据：角色 ==========
INSERT INTO `role` (`name`, `code`, `description`) VALUES
('管理员', 'admin', '管理用户、题库、全部数据'),
('面试官', 'interviewer', '查看记录、评分'),
('候选人', 'candidate', '参加面试、看自己的报告');

-- ========== 六、初始数据：权限（17 项） ==========
INSERT INTO `permission` (`name`, `code`, `type`) VALUES
-- 用户管理
('查看用户',        'user:view',         'api'),
('修改用户',        'user:update',       'api'),
-- 题库
('查看题目',        'question:view',     'api'),
('新增题目',        'question:create',   'api'),
('修改题目',        'question:update',   'api'),
('删除题目',        'question:delete',   'api'),
-- 场景
('查看场景',        'scenario:view',     'api'),
('新增场景',        'scenario:create',   'api'),
('修改场景',        'scenario:update',   'api'),
('删除场景',        'scenario:delete',   'api'),
-- 面试记录
('创建面试记录',    'record:create',     'api'),
('查看面试记录',    'record:view',       'api'),
('面试评分',        'record:score',      'api'),
-- 面试动作
('开始面试',        'interview:start',   'api'),
('提交面试',        'interview:submit',  'api'),
('取消面试',        'interview:cancel',  'api'),
-- 报告
('查看报告',        'report:view',       'api');

-- ========== 七、初始数据：角色-权限关联 ==========
-- 管理员：拥有全部权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id` FROM `role` r, `permission` p
WHERE r.`code` = 'admin' AND p.`code` IN (
    'user:view', 'user:update',
    'question:view', 'question:create', 'question:update', 'question:delete',
    'scenario:view', 'scenario:create', 'scenario:update', 'scenario:delete',
    'record:create', 'record:view', 'record:score',
    'interview:start', 'interview:submit', 'interview:cancel',
    'report:view'
);

-- 面试官：查看/评分记录、查看题目、看报告
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id` FROM `role` r, `permission` p
WHERE r.`code` = 'interviewer' AND p.`code` IN (
    'record:view', 'record:score',
    'question:view', 'scenario:view',
    'report:view'
);

-- 候选人：能看场景/题目、能走完一场面试、看自己的记录与报告
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id` FROM `role` r, `permission` p
WHERE r.`code` = 'candidate' AND p.`code` IN (
    'scenario:view', 'question:view',
    'record:create', 'record:view',
    'interview:start', 'interview:submit', 'interview:cancel',
    'report:view'
);

-- ========== 八、初始数据：用户-角色关联 ==========
-- 为 init.sql 创建的测试用户分配角色（RBAC 下用户-角色关系完全由 user_role 表达）
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id` FROM `sys_user` u JOIN `role` r
  ON r.`code` = 'admin'     WHERE u.`username` = 'admin'
UNION ALL
SELECT u.`id`, r.`id` FROM `sys_user` u JOIN `role` r
  ON r.`code` = 'candidate' WHERE u.`username` = 'test01'
UNION ALL
SELECT u.`id`, r.`id` FROM `sys_user` u JOIN `role` r
  ON r.`code` = 'interviewer' WHERE u.`username` = 'interviewer';
