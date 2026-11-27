-- ============================================================================
--  智慧教育平台 —— 性能测试数据清理脚本
-- ============================================================================
--  作用：把 01_seed_perf_data.sql 造出来的压测数据从测试库里彻底删掉，
--        让数据库回到「只剩功能测试数据」的状态。
--
--  为什么必须清理：
--    压测数据会让「课程总数 / 职位总数 / 用户总数」这类计数断言失效。
--    只读接口列表里会混进 1000 门「压测课程」，分页断言也会跟着挂。
--    清理之后接口套件（196 条）与 UI 套件（108 条）才能正常跑。
--
--  删除范围（全部是 01 脚本造出来的数据，不会碰功能测试数据）：
--    user         username LIKE 'perf\_%'        —— 205 个压测账号
--    course       course_name LIKE '压测课程%'    —— 1000 门压测课程
--    course_chapter、job 及其全部关联数据
--
--  安全性：只删能匹配到上述命名模式的数据。
--    student01 / teacher01 / admin、以及接口测试自己注册的账号
--    都不匹配 'perf\_%'，不会被误删。
--
--  幂等：可重复执行，第二次执行删除 0 行。
--  执行方式：Navicat 里打开本文件直接运行。
-- ============================================================================

USE smart_education_platform_test;

-- ---------------------------------------------------------------------------
-- 0. 清理前盘点：先看清楚要删多少行
-- ---------------------------------------------------------------------------

SELECT '--- 清理前 ---' AS 阶段;

SELECT 'user（压测账号）'                    AS 对象, COUNT(*) AS 行数 FROM user   WHERE username    LIKE 'perf\_%'
UNION ALL SELECT 'course（压测课程）',                COUNT(*) FROM course WHERE course_name LIKE '压测课程%'
UNION ALL SELECT 'course_chapter（压测章节）',        COUNT(*) FROM course_chapter
                                                     WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%')
UNION ALL SELECT 'job（压测职位）',                   COUNT(*) FROM job    WHERE job_name    LIKE '压测职位%'
UNION ALL SELECT 'orders（压测账号的订单）',          COUNT(*) FROM orders
                                                     WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%')
UNION ALL SELECT 'cart（压测账号的购物车）',          COUNT(*) FROM cart
                                                     WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%');


-- ---------------------------------------------------------------------------
-- 1. 删除
--    先删子表再删父表，否则触发外键约束。
--    外围再包一层 FOREIGN_KEY_CHECKS=0 兜底：不同版本建表时外键命名不一致，
--    靠顺序删除足够了，但关掉检查能避免因为某个索引差异而中途失败。
-- ---------------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 0;

-- 压测账号产生的行为数据
DELETE FROM cart           WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM course_collect WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM job_collect    WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM study_record   WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM job_apply      WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM ai_interview   WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM resume         WHERE user_id     IN (SELECT id FROM user WHERE username LIKE 'perf\_%');
DELETE FROM apply          WHERE applicant_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%');

-- 压测课程的关联数据（就算没有压测账号引用，课程本身也得连着清）
DELETE FROM course_collect WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%');
DELETE FROM study_record   WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%');

-- 订单：order_item 是 orders 的子表，先删
DELETE FROM order_item WHERE order_id IN (
    SELECT id FROM orders WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%'));
DELETE FROM orders     WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'perf\_%');

-- 课程章节 → 课程
DELETE FROM course_chapter WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%');
DELETE FROM course         WHERE course_name LIKE '压测课程%';

-- 职位 → 账号
DELETE FROM job  WHERE job_name  LIKE '压测职位%';
DELETE FROM user WHERE username  LIKE 'perf\_%';

SET FOREIGN_KEY_CHECKS = 1;


-- ---------------------------------------------------------------------------
-- 2. 清理后核对
--    前 4 行应当全部为 0；
--    后 3 行的「全表合计」应当回到功能测试的数据量（课程 2 门、职位 1 个左右）。
-- ---------------------------------------------------------------------------

SELECT '--- 清理后 ---' AS 阶段;

SELECT 'user（压测账号）'                    AS 对象, COUNT(*) AS 行数 FROM user   WHERE username    LIKE 'perf\_%'
UNION ALL SELECT 'course（压测课程）',                COUNT(*) FROM course WHERE course_name LIKE '压测课程%'
UNION ALL SELECT 'course_chapter（压测章节）',        COUNT(*) FROM course_chapter
                                                     WHERE course_id IN (SELECT id FROM course WHERE course_name LIKE '压测课程%')
UNION ALL SELECT 'job（压测职位）',                   COUNT(*) FROM job    WHERE job_name    LIKE '压测职位%'
UNION ALL SELECT '—— 全表合计 ——',                   NULL
UNION ALL SELECT 'user 总数',                 COUNT(*) FROM user
UNION ALL SELECT 'course 总数',               COUNT(*) FROM course
UNION ALL SELECT 'job 总数',                  COUNT(*) FROM job;
