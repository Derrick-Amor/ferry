-- ============================================================================
-- 渡口 Ferry · 数据库结构定义（Schema / DDL）
-- ----------------------------------------------------------------------------
-- 用途：
--   本脚本用于从零创建一个空白的 Ferry 数据库结构，共 15 张业务表。
--   不含任何业务数据，种子数据请在本脚本执行成功后执行 data.sql。
--
-- 环境要求：
--   MySQL 8.0.16 及以上（CHECK 约束自 8.0.16 起才会被真正强制执行；
--   字符集 utf8mb4_0900_ai_ci 需要 8.0 及以上）。
--
-- 执行方式（数据库名由部署环境决定，本脚本不含 CREATE DATABASE / USE）：
--   mysql -u<user> -p <db_name> < schema.sql
--   mysql -u<user> -p <db_name> < data.sql
--
-- 执行顺序：
--   1. schema.sql  —— 建表（本文件）
--   2. data.sql    —— 灌入演示种子数据
--
-- 设计说明：
--   * 所有表统一 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
--   * 建表顺序严格遵循外键依赖：被引用的父表在前
--       user -> company -> course -> course_chapter -> job -> 订单/购物车/收藏/
--       学习记录/申请/AI 面试
--   * DROP TABLE 顺序与建表顺序相反（先删子表再删父表），
--     因此本脚本可反复执行而不会因外键依赖失败
--   * 主键统一 BIGINT UNSIGNED AUTO_INCREMENT，AUTO_INCREMENT 起始值交由
--     MySQL 自行分配（不写 AUTO_INCREMENT=n，避免导出残留影响新库）
-- ============================================================================

-- 强制客户端按 utf8mb4 解析本文件。
-- Docker 容器内 initdb 时，客户端字符集可能因缺少 UTF-8 locale 退化为 latin1，
-- 导致 UTF-8 中文经「latin1 解释 -> utf8mb4 存储」变成双重编码乱码。
-- 显式声明确保脚本在任何客户端默认值下都能正确落库。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 清理：按“子表 -> 父表”的顺序删除，保证可重复执行
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_interview`;
DROP TABLE IF EXISTS `apply`;
DROP TABLE IF EXISTS `study_record`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `job_collect`;
DROP TABLE IF EXISTS `job_apply`;
DROP TABLE IF EXISTS `course_collect`;
DROP TABLE IF EXISTS `cart`;
DROP TABLE IF EXISTS `resume`;
DROP TABLE IF EXISTS `job`;
DROP TABLE IF EXISTS `course_chapter`;
DROP TABLE IF EXISTS `course`;
DROP TABLE IF EXISTS `company`;
DROP TABLE IF EXISTS `user`;

-- ---------------------------------------------------------------------------
-- 1. user 用户表
--    三种角色共表：0 学生 / 1 讲师或企业 / 2 管理员
-- ---------------------------------------------------------------------------
CREATE TABLE `user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名(4~20位)',
  `password` varchar(100) NOT NULL COMMENT '密码BCrypt密文(60字符)',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称(2~20位)',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号(大陆11位)',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `role` tinyint NOT NULL DEFAULT '0' COMMENT '角色:0学生 1讲师/企业 2管理员',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0禁用 1正常',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_nickname` (`nickname`),
  KEY `idx_user_phone` (`phone`),
  KEY `idx_user_email` (`email`),
  CONSTRAINT `chk_user_role` CHECK (`role` in (0,1,2)),
  CONSTRAINT `chk_user_status` CHECK (`status` in (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ---------------------------------------------------------------------------
-- 2. company 公司表
-- ---------------------------------------------------------------------------
CREATE TABLE `company` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '公司ID',
  `company_name` varchar(100) NOT NULL COMMENT '公司名称',
  `industry` varchar(50) DEFAULT NULL COMMENT '行业',
  `scale` varchar(20) DEFAULT NULL COMMENT '规模',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `logo` varchar(255) DEFAULT NULL COMMENT '公司Logo',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_company_name` (`company_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司表';

-- ---------------------------------------------------------------------------
-- 3. course 课程表（teacher_id -> user.id）
-- ---------------------------------------------------------------------------
CREATE TABLE `course` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `course_name` varchar(100) NOT NULL COMMENT '课程名称',
  `course_type` tinyint NOT NULL COMMENT '类型:1理论 2实训',
  `tech_system` varchar(50) DEFAULT NULL COMMENT '技术体系',
  `tech_direction` varchar(50) DEFAULT NULL COMMENT '技术方向',
  `course_level` varchar(20) DEFAULT NULL COMMENT '课程等级',
  `is_free` tinyint NOT NULL DEFAULT '0' COMMENT '0免费 1付费',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '价格',
  `teacher_id` bigint unsigned DEFAULT NULL COMMENT '讲师ID',
  `teacher_name` varchar(50) DEFAULT NULL COMMENT '讲师名称',
  `cover_image` varchar(255) DEFAULT NULL COMMENT '封面图URL',
  `description` text COMMENT '课程简介',
  `rating` decimal(2,1) NOT NULL DEFAULT '0.0' COMMENT '评分(0~5)',
  `enroll_count` int unsigned NOT NULL DEFAULT '0' COMMENT '报名学习人数',
  `list_time` datetime DEFAULT NULL COMMENT '上架时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核(不展示) 1已上架(展示) 2已下架(不展示) 3驳回(不展示)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_type` (`course_type`),
  KEY `idx_course_tech` (`tech_system`,`tech_direction`),
  KEY `idx_course_status` (`status`),
  KEY `idx_course_list_time` (`list_time`),
  KEY `idx_course_enroll` (`enroll_count`),
  KEY `idx_course_rating` (`rating`),
  KEY `fk_course_teacher` (`teacher_id`),
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_course_free` CHECK (`is_free` in (0,1)),
  CONSTRAINT `chk_course_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_course_rating` CHECK (`rating` between 0 and 5),
  CONSTRAINT `chk_course_status` CHECK (`status` in (0,1,2,3)),
  CONSTRAINT `chk_course_type` CHECK (`course_type` in (1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程表';

-- ---------------------------------------------------------------------------
-- 4. course_chapter 课程章节表（course_id -> course.id，级联删除）
-- ---------------------------------------------------------------------------
CREATE TABLE `course_chapter` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  `course_id` bigint unsigned NOT NULL COMMENT '课程ID',
  `chapter_name` varchar(100) NOT NULL COMMENT '章节/实验名称',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频/实验资源地址',
  `duration` int unsigned NOT NULL DEFAULT '0' COMMENT '时长(秒)',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_chapter_course` (`course_id`),
  CONSTRAINT `fk_chapter_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程章节表';

-- ---------------------------------------------------------------------------
-- 5. job 职位表（company_id -> company.id，公司注销时置空）
-- ---------------------------------------------------------------------------
CREATE TABLE `job` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '职位ID',
  `job_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `company_id` bigint unsigned DEFAULT NULL COMMENT '公司ID',
  `company_name` varchar(100) DEFAULT NULL COMMENT '公司名称(冗余)',
  `job_category` varchar(50) DEFAULT NULL COMMENT '职位分类',
  `salary_min` decimal(10,2) DEFAULT NULL COMMENT '薪资下限',
  `salary_max` decimal(10,2) DEFAULT NULL COMMENT '薪资上限',
  `city` varchar(50) DEFAULT NULL COMMENT '工作城市',
  `education` varchar(20) DEFAULT NULL COMMENT '学历要求',
  `experience` varchar(20) DEFAULT NULL COMMENT '经验要求',
  `description` text COMMENT '岗位描述',
  `apply_count` int unsigned NOT NULL DEFAULT '0' COMMENT '申请人数',
  `collect_count` int unsigned NOT NULL DEFAULT '0' COMMENT '收藏人数',
  `list_time` datetime DEFAULT NULL COMMENT '上架时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核(不展示) 1已上架(展示) 2已下架(不展示) 3驳回(不展示)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_category` (`job_category`),
  KEY `idx_job_city` (`city`),
  KEY `idx_job_status` (`status`),
  KEY `idx_job_list_time` (`list_time`),
  KEY `fk_job_company` (`company_id`),
  CONSTRAINT `fk_job_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_job_salary_max` CHECK (`salary_max` >= 0),
  CONSTRAINT `chk_job_salary_min` CHECK (`salary_min` >= 0),
  CONSTRAINT `chk_job_salary_range` CHECK ((`salary_min` is null) or (`salary_max` is null) or (`salary_min` <= `salary_max`)),
  CONSTRAINT `chk_job_status` CHECK (`status` in (0,1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位表';

-- ---------------------------------------------------------------------------
-- 6. resume 简历表（user_id -> user.id，级联删除）
-- ---------------------------------------------------------------------------
CREATE TABLE `resume` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '简历ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_url` varchar(255) NOT NULL COMMENT '存储相对路径',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_user` (`user_id`),
  CONSTRAINT `fk_resume_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历表';

-- ---------------------------------------------------------------------------
-- 7. cart 购物车表（user_id -> user.id，course_id -> course.id）
--    同一用户同一课程只允许一条记录
-- ---------------------------------------------------------------------------
CREATE TABLE `cart` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `course_id` bigint unsigned NOT NULL COMMENT '课程ID',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '数量',
  `checked` tinyint NOT NULL DEFAULT '1' COMMENT '是否勾选结算:0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_course` (`user_id`,`course_id`),
  KEY `fk_cart_course` (`course_id`),
  CONSTRAINT `fk_cart_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_cart_checked` CHECK (`checked` in (0,1)),
  CONSTRAINT `chk_cart_qty` CHECK (`quantity` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';

-- ---------------------------------------------------------------------------
-- 8. course_collect 课程收藏表
-- ---------------------------------------------------------------------------
CREATE TABLE `course_collect` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `course_id` bigint unsigned NOT NULL COMMENT '课程ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_collect_user_course` (`user_id`,`course_id`),
  KEY `fk_course_collect_course` (`course_id`),
  CONSTRAINT `fk_course_collect_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_course_collect_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程收藏表';

-- ---------------------------------------------------------------------------
-- 9. job_apply 职位投递表（依赖 user / job / resume）
-- ---------------------------------------------------------------------------
CREATE TABLE `job_apply` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `job_id` bigint unsigned NOT NULL COMMENT '职位ID',
  `resume_id` bigint unsigned DEFAULT NULL COMMENT '简历ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0已投递 1已查看 2面试邀请 3已录用 4已拒绝',
  `apply_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_apply_user` (`user_id`),
  KEY `idx_job_apply_job` (`job_id`),
  KEY `fk_job_apply_resume` (`resume_id`),
  CONSTRAINT `fk_job_apply_job` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_job_apply_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_job_apply_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_job_apply_status` CHECK (`status` in (0,1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位投递表';

-- ---------------------------------------------------------------------------
-- 10. job_collect 职位收藏表
-- ---------------------------------------------------------------------------
CREATE TABLE `job_collect` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `job_id` bigint unsigned NOT NULL COMMENT '职位ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_collect_user_job` (`user_id`,`job_id`),
  KEY `fk_job_collect_job` (`job_id`),
  CONSTRAINT `fk_job_collect_job` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_job_collect_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位收藏表';

-- ---------------------------------------------------------------------------
-- 11. orders 订单表（user_id -> user.id，有订单的用户不允许物理删除）
-- ---------------------------------------------------------------------------
CREATE TABLE `orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '总金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待支付 1已支付 2已取消 3已退款',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_no` (`order_no`),
  KEY `idx_orders_user` (`user_id`),
  KEY `idx_orders_status` (`status`),
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_orders_amount` CHECK (`total_amount` >= 0),
  CONSTRAINT `chk_orders_status` CHECK (`status` in (0,1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

-- ---------------------------------------------------------------------------
-- 12. order_item 订单明细表（依赖 orders / course）
-- ---------------------------------------------------------------------------
CREATE TABLE `order_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `course_id` bigint unsigned NOT NULL COMMENT '课程ID',
  `course_name` varchar(100) DEFAULT NULL COMMENT '课程名称(快照)',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '成交单价',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '数量',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`),
  KEY `idx_order_item_course` (`course_id`),
  CONSTRAINT `fk_order_item_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_order_item_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_order_item_qty` CHECK (`quantity` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表';

-- ---------------------------------------------------------------------------
-- 13. study_record 学习记录表（user / course / course_chapter）
--     同一用户同一课程只保留一条进度记录
-- ---------------------------------------------------------------------------
CREATE TABLE `study_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `course_id` bigint unsigned NOT NULL COMMENT '课程ID',
  `chapter_id` bigint unsigned DEFAULT NULL COMMENT '章节ID',
  `progress` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '学习进度(0~100)',
  `last_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近学习时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_record_user_course` (`user_id`,`course_id`),
  KEY `idx_study_record_chapter` (`chapter_id`),
  KEY `fk_study_record_course` (`course_id`),
  CONSTRAINT `fk_study_record_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapter` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_study_record_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_study_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_study_record_progress` CHECK (`progress` between 0 and 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习记录表';

-- ---------------------------------------------------------------------------
-- 14. apply 上下架申请表
--     target_id 为多态字段（课程或职位），故意不加外键
-- ---------------------------------------------------------------------------
CREATE TABLE `apply` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `apply_type` tinyint NOT NULL COMMENT '1课程上架 2职位上架 3课程下架 4职位下架(上下架=控制信息展示)',
  `target_id` bigint unsigned NOT NULL COMMENT '目标课程/职位ID(多态,不加外键)',
  `applicant_id` bigint unsigned NOT NULL COMMENT '申请人ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请理由',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核 1通过 2驳回',
  `auditor_id` bigint unsigned DEFAULT NULL COMMENT '审核人ID',
  `audit_comment` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`id`),
  KEY `idx_apply_applicant` (`applicant_id`),
  KEY `idx_apply_status` (`status`),
  KEY `idx_apply_type_target` (`apply_type`,`target_id`),
  KEY `fk_apply_auditor` (`auditor_id`),
  CONSTRAINT `fk_apply_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_apply_auditor` FOREIGN KEY (`auditor_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_apply_status` CHECK (`status` in (0,1,2)),
  CONSTRAINT `chk_apply_type` CHECK (`apply_type` in (1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上下架申请表';

-- ---------------------------------------------------------------------------
-- 15. ai_interview AI 面试表（依赖 user / job）
-- ---------------------------------------------------------------------------
CREATE TABLE `ai_interview` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '面试ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `job_id` bigint unsigned NOT NULL COMMENT '职位ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待作答 1进行中 2已完成',
  `score` decimal(5,2) DEFAULT NULL COMMENT '面试评分(0~100)',
  `result` text COMMENT '面试结果/报告',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_interview_user` (`user_id`),
  KEY `idx_ai_interview_job` (`job_id`),
  CONSTRAINT `fk_ai_interview_job` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ai_interview_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_ai_interview_score` CHECK (`score` between 0 and 100),
  CONSTRAINT `chk_ai_interview_status` CHECK (`status` in (0,1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI面试表';

-- ============================================================================
-- Schema 结束：共 15 张表、23 个外键约束、23 个 CHECK 约束
-- 下一步：执行 data.sql 灌入演示数据
-- ============================================================================
