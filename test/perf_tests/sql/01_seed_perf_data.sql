-- ============================================================================
--  智慧教育平台 —— 性能测试数据准备脚本
-- ============================================================================
--  作用：把测试库扩充到「可压测」的数据量。
--
--  为什么必须造数据：
--    重置脚本给出的库只有 2 门课、1 个职位。拿这个数据量压分页接口，
--    MySQL 无论怎么查都是全表扫 2 行，响应时间恒定在亚毫秒级，
--    「分页」「筛选」「模糊搜索」的性能特征完全体现不出来，测了等于没测。
--
--  造出来的数据规模：
--    user            205 条（200 个压测学生 + 5 个压测讲师）
--    course         1000 条（已上架 status=1，技术体系/方向/等级/免费与否 均有分布）
--    course_chapter  600 条（每 5 门课挂 3 个章节）
--    job             500 条（已上架 status=1，城市/分类/薪资区间分散）
--
--  与功能测试的关系：
--    本脚本插入的数据会让「课程总数 / 职位总数」这类计数断言失效。
--    跑完性能测试后，请执行 02_cleanup_perf_data.sql 清理，
--    再跑接口与 UI 自动化套件。
--
--  幂等：可重复执行。每次执行会先删掉上一批压测数据再重建。
--  执行方式：Navicat 里打开本文件直接运行（无需手工改任何参数）。
-- ============================================================================

USE smart_education_platform_test;

-- ---------------------------------------------------------------------------
-- 0. 清理上一批压测数据（保证脚本可重复执行）
--    注意删除顺序：先删子表，再删父表，否则触发外键约束
-- ---------------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM cart           WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM course_collect WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM course_collect WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%');
DELETE FROM job_collect    WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM study_record   WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM study_record   WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%');
DELETE FROM job_apply      WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM ai_interview   WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM resume         WHERE user_id   IN (SELECT id FROM user   WHERE username LIKE 'perf\_%');
DELETE FROM apply          WHERE applicant_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%');

DELETE FROM order_item WHERE order_id IN (
    SELECT id FROM orders WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%'));
DELETE FROM orders WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%');

DELETE FROM course_chapter WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%');
DELETE FROM course         WHERE course_name LIKE '压测课程%';
DELETE FROM job            WHERE job_name    LIKE '压测职位%';
DELETE FROM user           WHERE username    LIKE 'perf\_%';

SET FOREIGN_KEY_CHECKS = 1;


-- ---------------------------------------------------------------------------
-- 1. 准备一个「数字表」nums(1..1000)
--    用一个 10×10×10 的交叉连接生成，不需要建存储过程，
--    任何 MySQL 客户端都能直接执行。
-- ---------------------------------------------------------------------------

DROP TEMPORARY TABLE IF EXISTS nums;
CREATE TEMPORARY TABLE nums (n INT PRIMARY KEY);

INSERT INTO nums (n)
SELECT a.i + b.i * 10 + c.i * 100 + 1
FROM       (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c;


-- ---------------------------------------------------------------------------
-- 2. 用户：5 个讲师 + 200 个学生
--
--    密码全部复用 student01 的 BCrypt 密文，明文是 123456。
--    （BCrypt 每次加密结果都不同，但都能用同一个明文校验通过，
--      所以这里直接复用一条已知可用的密文，避免在 SQL 里算 BCrypt。）
--
--    nickname 必须唯一（表上有 uk_user_nickname），这里用编号保证不冲突。
-- ---------------------------------------------------------------------------

SET @pwd = '$2a$10$pc0Y7NhOLgg0U5cR3odbCeDSLYWeMbcLxt.U8QUgFqJaE4uobj9EO';   -- 明文 123456

-- 5 个讲师（role=1），后面课程要引用它们的 id
INSERT INTO user (username, password, nickname, phone, email, role, status)
SELECT CONCAT('perf_teacher', n),
       @pwd,
       CONCAT('压测讲师', n),
       CONCAT('137', LPAD(n, 8, '0')),
       CONCAT('perf_teacher', n, '@perf.test'),
       1, 1
FROM nums WHERE n <= 5;

-- 200 个学生（role=0），压登录接口用
INSERT INTO user (username, password, nickname, phone, email, role, status)
SELECT CONCAT('perf_stu', LPAD(n, 4, '0')),
       @pwd,
       CONCAT('压测学生', LPAD(n, 4, '0')),
       CONCAT('139', LPAD(n, 8, '0')),
       CONCAT('perf_stu', LPAD(n, 4, '0'), '@perf.test'),
       0, 1
FROM nums WHERE n <= 200;

-- 记下讲师 id 的起点：上面是一次多行插入，id 必然连续
SET @teacher_base = (SELECT MIN(id) FROM user WHERE username LIKE 'perf\_teacher%');


-- ---------------------------------------------------------------------------
-- 3. 课程：1000 条，全部 status=1（已上架，否则列表接口查不到）
--
--    各筛选维度都要有分布，否则测试用例只能命中一种组合：
--      course_type    1=理论 2=实训      ← 按奇偶分
--      tech_system    Java / Python / 前端 / 大数据 / 人工智能
--      tech_direction 后端 / 前端 / 数据 / 算法
--      course_level   初级 / 中级 / 高级
--      is_free        0=付费 1=免费
--      enroll_count   0~9999 随机偏移     ← 排序测试用
--      rating         3.0~5.0             ← 排序测试用
--      list_time      每条差 1 分钟        ← 按时间排序测试用
-- ---------------------------------------------------------------------------

INSERT INTO course
    (course_name, course_type, tech_system, tech_direction, course_level,
     is_free, price, teacher_id, teacher_name, rating, enroll_count,
     list_time, status, description)
SELECT
    -- 课程名里带技术栈关键词，供 keyword 模糊搜索使用
    CONCAT('压测课程', LPAD(n, 4, '0'), ' ',
           ELT(n % 5 + 1, 'Java', 'Python', '前端', '大数据', '人工智能'), ' 实战'),
    IF(n % 2 = 0, 2, 1),                                            -- course_type 1/2
    ELT(n % 5 + 1, 'Java', 'Python', '前端', '大数据', '人工智能'),   -- tech_system
    ELT(n % 4 + 1, '后端', '前端', '数据', '算法'),                   -- tech_direction
    ELT(n % 3 + 1, '初级', '中级', '高级'),                           -- course_level
    IF(n % 3 = 0, 1, 0),                                            -- 每 3 门 1 门免费
    IF(n % 3 = 0, 0.00, 49.00 + (n % 20) * 10),                     -- 价格 49~239
    @teacher_base + (n % 5),                                        -- teacher_id
    CONCAT('压测讲师', n % 5 + 1),                                    -- teacher_name
    ROUND(3.0 + (n % 21) * 0.1, 1),                                 -- rating 3.0~5.0
    (n * 7919) % 10000,                                             -- enroll_count 伪随机
    DATE_SUB(NOW(), INTERVAL n MINUTE),                             -- list_time 每条差 1 分钟
    1,                                                              -- status=1 已上架
    CONCAT('这是用于性能测试的第 ', n, ' 门课程，技术体系 ',
           ELT(n % 5 + 1, 'Java', 'Python', '前端', '大数据', '人工智能'), '。')
FROM nums;


-- ---------------------------------------------------------------------------
-- 4. 章节：每 5 门课挂 3 章（200 门课 × 3 = 600 条）
--    只给一部分课挂章节 —— 详情页带章节子查询，要和「无章节」的课程
--    形成对比，才能看出章节查询对响应时间的影响。
-- ---------------------------------------------------------------------------

INSERT INTO course_chapter (course_id, chapter_name, video_url, duration, sort_no)
SELECT c.id,
       CONCAT('第', ch.k, '章 压测章节'),
       CONCAT('/uploads/video/perf', c.id, '_', ch.k, '.mp4'),
       15 + ch.k * 5,
       ch.k
FROM course c
CROSS JOIN (SELECT 1 k UNION ALL SELECT 2 UNION ALL SELECT 3) ch
WHERE c.course_name LIKE '压测课程%'
  AND CAST(SUBSTRING(c.course_name, 5, 4) AS UNSIGNED) % 5 = 0;


-- ---------------------------------------------------------------------------
-- 5. 职位：500 条，全部 status=1
-- ---------------------------------------------------------------------------

INSERT INTO job
    (job_name, company_id, company_name, job_category, salary_min, salary_max,
     city, education, experience, description, apply_count, collect_count,
     list_time, status)
SELECT
    CONCAT('压测职位', LPAD(n, 4, '0'), ' ',
           ELT(n % 4 + 1, 'Java 后端开发工程师', '前端开发工程师',
                          '大数据开发工程师', '算法工程师')),
    NULL,                                                           -- company_id 允许为空
    CONCAT('压测科技有限公司', n % 20 + 1),
    ELT(n % 4 + 1, '开发', '测试', '运维', '算法'),                     -- job_category
    8000 + (n % 10) * 1000,                                         -- salary_min  8000~17000
    15000 + (n % 10) * 1000,                                        -- salary_max  15000~24000
    ELT(n % 6 + 1, '北京', '上海', '深圳', '杭州', '广州', '成都'),      -- city
    ELT(n % 3 + 1, '本科', '硕士', '大专'),                            -- education
    ELT(n % 4 + 1, '1年', '3年', '5年', '不限'),                       -- experience
    CONCAT('这是用于性能测试的第 ', n, ' 个职位描述。'),
    (n * 6151) % 500,
    (n * 3571) % 500,
    DATE_SUB(NOW(), INTERVAL n MINUTE),
    1
FROM nums WHERE n <= 500;


DROP TEMPORARY TABLE IF EXISTS nums;


-- ---------------------------------------------------------------------------
-- 6. 结果核对
-- ---------------------------------------------------------------------------

SELECT 'user（压测账号）'  AS 表, COUNT(*) AS 行数 FROM user   WHERE username LIKE 'perf\_%'
UNION ALL SELECT 'course（压测课程）',  COUNT(*) FROM course WHERE course_name LIKE '压测课程%'
UNION ALL SELECT 'course_chapter（压测章节）', COUNT(*) FROM course_chapter
                                              WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%')
UNION ALL SELECT 'job（压测职位）',     COUNT(*) FROM job    WHERE job_name LIKE '压测职位%'
UNION ALL SELECT '—— 全表合计 ——', NULL
UNION ALL SELECT 'user 总数',      COUNT(*) FROM user
UNION ALL SELECT 'course 总数',    COUNT(*) FROM course
UNION ALL SELECT 'job 总数',       COUNT(*) FROM job;
