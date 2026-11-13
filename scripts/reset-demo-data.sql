-- ============================================================
-- 重置演示数据（换设备、切分支、重跑种子前使用）
--
-- 场景：之前的设备/环境跑过旧版本，库里还是旧的学生名单（如 2023001=刘一诺），
--      而 DemoDataInitializer 的判断是「库里已有学生就跳过」，
--      所以只切代码不清库，登录后看到的仍是旧数据。
--
-- 用法（二选一）：
--   本地 MySQL：
--     mysql -uroot -p <库名> < scripts/reset-demo-data.sql
--   Docker：
--     docker exec -i <mysql容器名> mysql -uroot -p<密码> <库名> < scripts/reset-demo-data.sql
--
-- 执行后重启后端，schema.sql 会补上评分标准，DemoDataInitializer 会重建：
--   8 名软件工程学生（2023001~2023008）、6 个实训室、25 位导师、8 个阶段、演示志愿。
--
-- 注意：这是清空操作，学生简历、私信、调剂记录会一并删除。仅用于演示环境。
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 会话与消息
DELETE FROM direct_messages;
DELETE FROM notifications;

-- 调剂与 AI 留痕
DELETE FROM adjust_records;
DELETE FROM ai_reviews;
DELETE FROM ai_personas;

-- 面试
DELETE FROM interview_scores;
DELETE FROM interview_results;
DELETE FROM interview_schedules;

-- 志愿与档案
DELETE FROM applications;
DELETE FROM resume_texts;
DELETE FROM student_resumes;
DELETE FROM student_profiles;

-- 组织与账号
DELETE FROM tutors;
DELETE FROM labs;
DELETE FROM users;

-- 阶段与运行配置
DELETE FROM selection_phases;
DELETE FROM system_config;

-- 审计日志
DELETE FROM operation_logs;

-- 自增归零，重建后 id 从 1 开始，便于对照文档
ALTER TABLE users AUTO_INCREMENT = 1;
ALTER TABLE labs AUTO_INCREMENT = 1;
ALTER TABLE tutors AUTO_INCREMENT = 1;
ALTER TABLE applications AUTO_INCREMENT = 1;
ALTER TABLE selection_phases AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;

-- 核对：以下四行在重启后端之前都应为 0
SELECT 'users' AS 表名, COUNT(*) AS 剩余 FROM users
UNION ALL SELECT 'labs', COUNT(*) FROM labs
UNION ALL SELECT 'tutors', COUNT(*) FROM tutors
UNION ALL SELECT 'applications', COUNT(*) FROM applications;
