-- ============================================================
-- 学生实训室双向选择系统 —— 数据库结构（重建版）
-- 设计原则：
--   1. 四类账号（STUDENT / TUTOR / LAB_ADMIN / SYS_ADMIN）共用 users 表，
--      登录后按 role 识别身份，导师与实训室管理员共用同一工作台。
--   2. 8 个业务阶段全部落在 selection_phases 表，时间窗口是唯一权威依据，
--      所有写接口由后端做绝对时间校验（不依赖前端隐藏按钮）。
--   3. 名额以 tutors.remaining_quota 为准，领取名额用条件 UPDATE 保证原子性。
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL COMMENT '登录账号（学号/工号）',
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  name          VARCHAR(50)  NOT NULL,
  role          VARCHAR(20)  NOT NULL COMMENT 'STUDENT/TUTOR/LAB_ADMIN/SYS_ADMIN',
  student_no    VARCHAR(30)  DEFAULT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  email         VARCHAR(100) DEFAULT NULL,
  lab_id        BIGINT       DEFAULT NULL COMMENT '所属实训室（导师/室管）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  last_login_at DATETIME     DEFAULT NULL,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_student_no (student_no),
  KEY idx_users_role (role),
  KEY idx_users_lab (lab_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号表（四类角色共用）';

CREATE TABLE IF NOT EXISTS labs (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  description TEXT COMMENT '实训室核心方向',
  location    VARCHAR(100) DEFAULT NULL,
  leader_id   BIGINT       DEFAULT NULL COMMENT '实训室负责人（users.id）',
  -- 以下为宣讲/开放日用的实训室级横向对比信息（导师级信息不重复存放）
  tech_backend        VARCHAR(400) DEFAULT NULL COMMENT '后端/工程技术栈',
  tech_frontend       VARCHAR(400) DEFAULT NULL COMMENT '前端/客户端技术栈',
  ai_direction        VARCHAR(400) DEFAULT NULL COMMENT 'AI/大模型方向',
  output_types        VARCHAR(400) DEFAULT NULL COMMENT '项目产出类型',
  achievements        VARCHAR(400) DEFAULT NULL COMMENT '竞赛/科研成果',
  training_mode       VARCHAR(400) DEFAULT NULL COMMENT '培养模式',
  graduation_standard VARCHAR(400) DEFAULT NULL COMMENT '考核/结业标准',
  suitable_for        VARCHAR(400) DEFAULT NULL COMMENT '适合人群',
  status      TINYINT      NOT NULL DEFAULT 1,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_labs_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实训室';

CREATE TABLE IF NOT EXISTS tutors (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  user_id            BIGINT       NOT NULL,
  lab_id             BIGINT       NOT NULL,
  title              VARCHAR(50)  DEFAULT NULL COMMENT '职称',
  research_direction VARCHAR(255) DEFAULT NULL COMMENT '项目方向',
  intro              TEXT,
  quota              INT          NOT NULL DEFAULT 0 COMMENT '总名额',
  remaining_quota    INT          NOT NULL DEFAULT 0 COMMENT '剩余名额（并发控制字段）',
  version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  status             TINYINT      NOT NULL DEFAULT 1,
  created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tutors_user (user_id),
  KEY idx_tutors_lab (lab_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导师（招生单元）';

CREATE TABLE IF NOT EXISTS student_profiles (
  user_id        BIGINT       NOT NULL,
  major          VARCHAR(50)  DEFAULT NULL,
  class_name     VARCHAR(50)  DEFAULT NULL,
  grade          VARCHAR(20)  DEFAULT NULL,
  gpa            DECIMAL(3,2) DEFAULT NULL,
  skill_tags     VARCHAR(255) DEFAULT NULL,
  resume_url     VARCHAR(255) DEFAULT NULL,
  priority_score DECIMAL(6,2) DEFAULT NULL COMMENT '兜底调剂排序权重',
  remark         VARCHAR(255) DEFAULT NULL,
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生档案';

-- 8 个阶段。code：BRIEFING/OPEN_DAY/APPLY/INTERVIEW/CONFIRM/SUPPLEMENT/ADJUST/PUBLISH
-- APPLY、INTERVIEW、CONFIRM 三个阶段逐轮重复（round_no 1~3），其余为全局阶段。
CREATE TABLE IF NOT EXISTS selection_phases (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  code       VARCHAR(30)  NOT NULL,
  name       VARCHAR(50)  NOT NULL,
  seq        INT          NOT NULL COMMENT '业务顺序 1~8',
  round_no   INT          DEFAULT NULL COMMENT '所属轮次，全局阶段为 NULL',
  start_at   DATETIME     NOT NULL,
  end_at     DATETIME     NOT NULL,
  status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/CLOSED',
  remark     VARCHAR(255) DEFAULT NULL,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_phase_code_round (code, round_no),
  KEY idx_phase_window (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阶段时间轴（唯一权威时间来源）';

CREATE TABLE IF NOT EXISTS applications (
  id                   BIGINT      NOT NULL AUTO_INCREMENT,
  student_id           BIGINT      NOT NULL,
  round_no             INT         NOT NULL DEFAULT 1,
  tutor_1_id            BIGINT      DEFAULT NULL,
  tutor_2_id            BIGINT      DEFAULT NULL,
  tutor_3_id            BIGINT      DEFAULT NULL,
  accept_lab_adjust    TINYINT     NOT NULL DEFAULT 0,
  accept_global_adjust TINYINT     NOT NULL DEFAULT 0,
  status               VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/MATCHED/REJECTED/EXPIRED',
  final_tutor_id       BIGINT      DEFAULT NULL,
  submitted_at         DATETIME    DEFAULT NULL,
  remark               VARCHAR(255) DEFAULT NULL,
  created_at           DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_student_round (student_id, round_no),
  KEY idx_app_student (student_id),
  KEY idx_app_round (round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿填报表（3 个平行志愿）';

CREATE TABLE IF NOT EXISTS interview_results (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  application_id   BIGINT       NOT NULL,
  student_id       BIGINT       NOT NULL,
  tutor_id         BIGINT       NOT NULL,
  round_no         INT          NOT NULL DEFAULT 1,
  score            DECIMAL(5,2) DEFAULT NULL,
  score_detail     VARCHAR(255) DEFAULT NULL,
  offer_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1拟录取(发Offer) 2递补 3淘汰',
  reserve_rank     INT          DEFAULT NULL COMMENT '递补顺位',
  confirm_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2超时放弃 3学生主动放弃',
  offer_time       DATETIME     DEFAULT NULL,
  confirm_deadline DATETIME     DEFAULT NULL,
  confirm_time     DATETIME     DEFAULT NULL,
  interviewer_id   BIGINT       DEFAULT NULL,
  interview_remark VARCHAR(255) DEFAULT NULL,
  version          INT          NOT NULL DEFAULT 0,
  created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_res_app_tutor (application_id, tutor_id),
  KEY idx_res_app (application_id),
  KEY idx_res_student (student_id),
  KEY idx_res_tutor (tutor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试与拟录取';

CREATE TABLE IF NOT EXISTS interview_schedules (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  lab_id         BIGINT       NOT NULL,
  round_no       INT          NOT NULL DEFAULT 1,
  tutor_id       BIGINT       DEFAULT NULL,
  interview_date DATE         DEFAULT NULL,
  start_time     TIME         DEFAULT NULL,
  end_time       TIME         DEFAULT NULL,
  location       VARCHAR(100) DEFAULT NULL,
  capacity       INT          DEFAULT NULL,
  status         TINYINT      NOT NULL DEFAULT 1,
  created_by     BIGINT       DEFAULT NULL,
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sched_lab (lab_id),
  KEY idx_sched_round (round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试排期（实训室统一面试）';

CREATE TABLE IF NOT EXISTS adjust_records (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  round_no       INT          DEFAULT NULL,
  student_id     BIGINT       NOT NULL,
  from_lab_id    BIGINT       DEFAULT NULL,
  to_lab_id      BIGINT       DEFAULT NULL,
  to_tutor_id    BIGINT       NOT NULL,
  adjust_type    TINYINT      NOT NULL COMMENT '1实训室内 2全院 3人工兜底',
  priority_score DECIMAL(6,2) DEFAULT NULL,
  status         TINYINT      NOT NULL DEFAULT 1,
  operator_id    BIGINT       DEFAULT NULL,
  reason         VARCHAR(255) DEFAULT NULL,
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_adjust_student (student_id),
  KEY idx_adjust_round (round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调剂记录';

CREATE TABLE IF NOT EXISTS notifications (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  type       VARCHAR(30)  DEFAULT NULL COMMENT 'OFFER/TIMEOUT/RESULT/NOTICE',
  title      VARCHAR(100) DEFAULT NULL,
  content    VARCHAR(500) DEFAULT NULL,
  biz_type   VARCHAR(30)  DEFAULT NULL,
  biz_id     BIGINT       DEFAULT NULL,
  is_read    TINYINT      NOT NULL DEFAULT 0,
  read_at    DATETIME     DEFAULT NULL,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notify_user (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

CREATE TABLE IF NOT EXISTS system_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  config_key   VARCHAR(50)  NOT NULL,
  config_value VARCHAR(500) DEFAULT NULL,
  value_type   VARCHAR(20)  DEFAULT 'STRING',
  config_group VARCHAR(30)  DEFAULT NULL,
  description  VARCHAR(255) DEFAULT NULL,
  updated_by   BIGINT       DEFAULT NULL,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局配置';

CREATE TABLE IF NOT EXISTS operation_logs (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       DEFAULT NULL,
  role        VARCHAR(20)  DEFAULT NULL,
  action      VARCHAR(50)  DEFAULT NULL,
  target_type VARCHAR(30)  DEFAULT NULL,
  target_id   BIGINT       DEFAULT NULL,
  detail      VARCHAR(500) DEFAULT NULL,
  ip          VARCHAR(50)  DEFAULT NULL,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_user (user_id),
  KEY idx_log_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ============================================================
-- AI 能力层（接口预留：学生端面试分析 / 实训室推荐 / 教师端简历初评 / 教师 AI 分身）
-- 说明：业务表只存"结果与配置"，具体模型调用在 ai/AiGateway 之后替换，不影响业务数据。
-- ============================================================

-- 面试评分维度与权重（评分标准的唯一来源，前端与 AI 都以这里为准）
CREATE TABLE IF NOT EXISTS score_rubrics (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30)  NOT NULL COMMENT '维度编码',
  name        VARCHAR(50)  NOT NULL COMMENT '维度名称',
  weight      INT          NOT NULL DEFAULT 0 COMMENT '权重（五项合计 100）',
  description VARCHAR(255) DEFAULT NULL COMMENT '考察点',
  anchors     TEXT         COMMENT '分数段行为锚点（JSON 数组，下标对应 0-59/60-74/75-89/90-100）',
  sort_no     INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rubric_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试评分标准（维度/权重/锚点）';

INSERT IGNORE INTO score_rubrics (code, name, weight, description, anchors, sort_no) VALUES
('MAJOR', '专业基础', 25, '核心课程掌握程度、专业概念是否成体系',
 '["概念模糊，需提示才能作答","基础概念正确，深度有限","能解释原理并举例","能迁移到新问题并指出边界"]', 1),
('SKILL', '实践技能', 25, '项目经历、代码与工具链的动手能力',
 '["仅课程作业，无独立实现","能按文档完成模块","独立完成过完整模块并有踩坑记录","有可演示的完整项目与性能/质量取舍"]', 2),
('POTENTIAL', '学习潜力', 20, '自学能力、调试思路、对未知问题的拆解',
 '["依赖给定步骤","能查资料解决已知问题","能自定学习路径并快速上手","能预判风险并设计验证方案"]', 3),
('MOTIVE', '动机匹配', 15, '对本实训室方向的了解程度与投入意愿',
 '["只知名称，无了解","了解方向但说不出细节","读过方向相关资料并能提问","有明确规划且与方向高度契合"]', 4),
('COMM', '表达沟通', 15, '逻辑结构、倾听与协作意识',
 '["回答零散，需要多次追问","能说清事实但缺结构","结构清晰，重点突出","能主动澄清问题并照顾听者"]', 5);

-- 教师 AI 分身：身份资料与应答边界（学生考察期咨询用）
CREATE TABLE IF NOT EXISTS ai_personas (
  tutor_id     BIGINT       NOT NULL COMMENT '导师 ID（tutors.id）',
  display_name VARCHAR(50)  DEFAULT NULL COMMENT '分身称呼，如"张伟老师助手"',
  tone         VARCHAR(100) DEFAULT NULL COMMENT '语气风格',
  expertise    VARCHAR(500) DEFAULT NULL COMMENT '可解答领域',
  boundaries   VARCHAR(500) DEFAULT NULL COMMENT '不回答 / 不承诺的事项',
  greeting     VARCHAR(500) DEFAULT NULL COMMENT '开场白',
  knowledge    TEXT         COMMENT '身份资料正文（方向、项目、对学生要求等）',
  model        VARCHAR(60)  DEFAULT NULL COMMENT '单独指定模型，空则用全局配置',
  status       TINYINT      NOT NULL DEFAULT 1,
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tutor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师 AI 分身身份设定';

-- 多维面试评分明细（与 interview_results.score 汇总分并存）
CREATE TABLE IF NOT EXISTS interview_scores (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  application_id BIGINT       NOT NULL,
  dimension_code VARCHAR(30)  NOT NULL,
  score          DECIMAL(5,2) DEFAULT NULL,
  comment        VARCHAR(255) DEFAULT NULL,
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_score_app_dim (application_id, dimension_code),
  KEY idx_score_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试多维评分明细';

-- AI 调用留痕（便于回溯与效果评估）
CREATE TABLE IF NOT EXISTS ai_reviews (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  kind           VARCHAR(30)  NOT NULL COMMENT 'INTERVIEW_TIPS/RECOMMEND/RESUME_REVIEW/AVATAR_CHAT',
  student_id     BIGINT       DEFAULT NULL,
  tutor_id       BIGINT       DEFAULT NULL,
  application_id BIGINT       DEFAULT NULL,
  engine         VARCHAR(30)  DEFAULT NULL COMMENT 'mock/openai/...',
  model          VARCHAR(60)  DEFAULT NULL,
  request        TEXT         COMMENT '入参摘要',
  response       MEDIUMTEXT   COMMENT '模型输出',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_kind (kind),
  KEY idx_ai_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用记录';

-- 学生简历（一位学生一份，重新上传即替换；文件落在 app.upload.resume-dir）
CREATE TABLE IF NOT EXISTS student_resumes (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  student_id   BIGINT       NOT NULL COMMENT 'users.id',
  file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名（展示用）',
  stored_name  VARCHAR(255) NOT NULL COMMENT '落盘文件名',
  content_type VARCHAR(100) DEFAULT NULL,
  size_bytes   BIGINT       DEFAULT NULL,
  uploaded_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resume_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生简历文件';

-- 简历正文抽取缓存（文本层 / PaddleOCR 结果，按简历 ID 缓存，重新上传即失效）
CREATE TABLE IF NOT EXISTS resume_texts (
  resume_id  BIGINT       NOT NULL COMMENT 'student_resumes.id',
  engine     VARCHAR(30)  NOT NULL COMMENT 'pdf-text / paddleocr / docx-text',
  pages      INT          NOT NULL DEFAULT 0,
  chars      INT          NOT NULL DEFAULT 0,
  text       MEDIUMTEXT,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历正文抽取缓存';
