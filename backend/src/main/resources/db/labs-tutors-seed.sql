-- ============================================================
-- 实训室 / 导师基础数据（真实名册）
-- 数据来源：实训室对比总表 2.0（6 个实训室 / 25 位导师）
--
-- 设计要点：
--   1. 全部语句幂等：按 labs.code、users.username、tutors.user_id 三个唯一键收敛，
--      重复执行只会对齐内容，不会产生重复行。
--   2. 重跑时 remaining_quota 按「已消耗名额不变」的方式平移，
--      不会把已经招出去的坑位重新填满。
--   3. 统一初始密码 123456（BCrypt），与演示账号一致；已存在的账号不改密码。
-- ============================================================

-- ------------------------------------------------------------
-- 一、6 个实训室（实训室级对比信息）
-- 202 / 204 / 402 / 404 / 406 / 505 为原表编号，同时作为 room 编号使用
-- ------------------------------------------------------------
INSERT INTO labs (code, name, description, tech_backend, tech_frontend, ai_direction,
                  output_types, achievements, training_mode, graduation_standard, suitable_for, status)
VALUES
('202', '202 实训室',
 '①企业级全栈系统研发（SpringBoot+Vue）②AI Agent 与大模型创新项目③工业机器视觉与自动化检测',
 'Java、SpringBoot、Node.js、Python、MySQL',
 'Vue、React、跨端混合开发',
 'AI Agent、大模型、工业机器视觉与自动化检测',
 '企业级全栈系统、AI Agent 创新项目、工业机器视觉检测系统、脱口派 APP',
 '408 考研辅导体系；企业级项目实战（移动、铁塔、联通相关）',
 '大二上到大三下四学期循序渐进；有固定工位；从会用 AI 工具到吃透语法原理',
 '掌握底层原理，能独立完成企业级项目',
 '兼顾就业、考研、考公三条出路；想打牢计算机基础的同学', 1),
('204', '204 实训室',
 'APP 端 + Web 管理端 + 后端服务 三端全栈项目',
 'Spring、微服务、MySQL、Redis、Minio、Linux、Nginx',
 '安卓开发、Vue 前端',
 'Codex AI 辅助开发（古法编程 + AI 结合）',
 '安卓 APP（含上架全流程：软著、ICP、公安备案）、Web 管理端、后端服务',
 '项目管理、团队协作流程实践',
 'Codex AI 辅助 + 古法编程结合；三端全流程实战',
 'Git 版本管理、数据库设计、三端初始化打包部署、AI 工具原型开发、团队分工与过程管理',
 '想做完整三端项目、了解 APP 上架全流程的同学', 1),
('402', '402 实训室',
 '①Java 后端/全栈 ②AI 应用/Agent ③产品/项目管理 ④部署/网络运维',
 'Java、SpringBoot、SpringCloud、MySQL、Redis、RabbitMQ',
 '移动端、前端基础',
 'Python、LangChain、RAG 知识库、AI Agent',
 '诗学汇、智谣通 App、养老陪伴虚拟人、智慧消防；微信小程序、客户端、网站、Agent 智能体',
 '中国国际大学生创新大赛、挑战杯、企业对接项目',
 '一对一针对性指导；10 年一线导师带教；成长主线：不敢开始→动手实践→独立思考→完成项目',
 '独立完成属于自己的项目作品；结业帮扶：简历修改、实习就业推荐',
 '想做企业实战项目、需要就业推荐的同学', 1),
('404', '404 实训室',
 '①AI 全栈 ②Java 后端 ③前端工程化 ④智能体知识库 ⑤产品设计',
 'Java、数据库、架构设计、SaaS 架构',
 '前端工程化、企业级前端研发',
 'AI 算法、智能体应用、知识库落地、小程序开发',
 '智能体、知识库方向企业真实项目；网络考试系统、村级事务平台、保险 SaaS、智慧农业平台、AI 家校平台',
 '主持 3 项国家级、省部级项目；学生获十余项竞赛奖项；竞赛指导、软著、论文产出',
 '导师个性化带队；以项目驱动竞赛、产出论文；对接企业真实项目',
 '完成企业级项目交付；参与竞赛、产出论文/软著',
 '想做工程化项目、竞赛论文产出的同学', 1),
('406', '406 实训室',
 '①UE 互动开发（仿真/数字孪生/游戏）②AI 影视创作（剧本/AIGC/AI 短剧）③人工智能研究（机器学习/大模型）',
 '偏 UE / 影视 / AI 方向',
 'UE/Unity、三维建模、特效场景、虚拟制片',
 '机器学习、大模型、小样本学习、AIGC 影视生成、AI 短剧（单条播放百万）',
 '虚拟仿真项目、UE/Unity 游戏、数字人、AI 短剧、生成式视频、科研项目',
 '科研项目（面向考研）、各类竞赛；百万级智慧农业企业项目；AI 短剧爆款产出',
 'AI + PBL 实训；两条分支：科研/竞赛线 或 虚拟仿真/游戏开发线；跨学科创新',
 '完成创意项目或科研产出',
 '创意 + 技术结合、UE/游戏开发、AI 影视创作、考研科研方向的同学', 1),
('505', '505 实训室',
 '①AI 全栈工程 ②RAG/Agent 开发 ③大模型应用 ④后端开发 ⑤需求与产品质量把控',
 '后端开发、业务系统开发、工单平台',
 '全栈开发',
 'RAG、Agent、大模型应用',
 '真实上线项目、业务系统、工单平台；可访问的项目作品',
 '真实上线项目、毕设底稿、项目记录体系',
 '做真实上线项目；匹配招聘要求的能力培养',
 '能沉下心、执行力强；独立完成上线项目',
 '想做出可访问项目、攒毕设底稿、匹配招聘能力的同学', 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  tech_backend = VALUES(tech_backend),
  tech_frontend = VALUES(tech_frontend),
  ai_direction = VALUES(ai_direction),
  output_types = VALUES(output_types),
  achievements = VALUES(achievements),
  training_mode = VALUES(training_mode),
  graduation_standard = VALUES(graduation_standard),
  suitable_for = VALUES(suitable_for),
  status = 1;

-- ------------------------------------------------------------
-- 二、导师账号（每室负责人挂 LAB_ADMIN，负责本室排期与室内调剂）
-- 登录账号 = 姓名全拼，统一初始密码 123456
-- ------------------------------------------------------------
INSERT INTO users (username, password, name, role, lab_id, status)
SELECT t.username, t.password, t.name, t.role, l.id, 1
FROM (
  SELECT 'wangyanhua'   AS username, '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6' AS password, '王艳华' AS name, 'TUTOR' AS role, '202' AS lab_code UNION ALL
  SELECT 'changlingling', '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '常玲玲', 'TUTOR', '202' UNION ALL
  SELECT 'liuguanjun',    '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '刘冠军', 'LAB_ADMIN', '202' UNION ALL
  SELECT 'fanjiahao',     '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '范佳浩', 'TUTOR', '202' UNION ALL
  SELECT 'huguoxin',      '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '胡国欣', 'LAB_ADMIN', '204' UNION ALL
  SELECT 'wangwei',       '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '王伟',   'TUTOR', '204' UNION ALL
  SELECT 'lihuanzhen',    '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '李焕贞', 'TUTOR', '204' UNION ALL
  SELECT 'jiahaitao',     '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '贾海涛', 'LAB_ADMIN', '402' UNION ALL
  SELECT 'pingyuxuan',    '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '平宇轩', 'TUTOR', '402' UNION ALL
  SELECT 'zhangxiang',    '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '张翔',   'TUTOR', '402' UNION ALL
  SELECT 'wangxiaolong',  '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '王晓龙', 'TUTOR', '402' UNION ALL
  SELECT 'gaoxuan',       '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '高璇',   'LAB_ADMIN', '404' UNION ALL
  SELECT 'wangpengfan',   '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '王鹏帆', 'TUTOR', '404' UNION ALL
  SELECT 'zhanghuibin',   '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '张会斌', 'TUTOR', '404' UNION ALL
  SELECT 'wangfengpei',   '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '王凤佩', 'TUTOR', '404' UNION ALL
  SELECT 'xuruize',       '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '徐瑞泽', 'TUTOR', '404' UNION ALL
  SELECT 'yangzhenying',  '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '杨振樱', 'LAB_ADMIN', '406' UNION ALL
  SELECT 'daiyifan',      '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '代一凡', 'TUTOR', '406' UNION ALL
  SELECT 'maqingyu',      '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '马庆宇', 'TUTOR', '406' UNION ALL
  SELECT 'pangkaiwen',    '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '庞凯文', 'TUTOR', '406' UNION ALL
  SELECT 'dingleilei',    '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '丁蕾蕾', 'TUTOR', '406' UNION ALL
  SELECT 'wanghao',       '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '王浩',   'LAB_ADMIN', '505' UNION ALL
  SELECT 'liujialiang',   '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '刘嘉亮', 'TUTOR', '505' UNION ALL
  SELECT 'liutianmeng',   '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '刘天萌', 'TUTOR', '505' UNION ALL
  SELECT 'lina',          '$2a$10$IyF3cRCD610YivOFc1T07eDbJVWZbz.RxoC.J1JXQJ4CsGL8YmkO6', '李娜',   'TUTOR', '505'
) t
JOIN labs l ON l.code = t.lab_code
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role = VALUES(role),
  lab_id = VALUES(lab_id),
  status = 1;

-- ------------------------------------------------------------
-- 三、导师招生单元（名额 = 原表「招生名额」）
-- title = 头衔，research_direction = 方向，intro = 原表「老师方向/头衔」整段
-- ------------------------------------------------------------
INSERT INTO tutors (user_id, lab_id, title, research_direction, intro, quota, remaining_quota, version, status)
SELECT u.id, u.lab_id, t.title, t.direction, t.intro, t.quota, t.quota, 0, 1
FROM (
  SELECT 'wangyanhua' AS username, '高级工程师' AS title,
         '408 考研（计网、计组）、AI 与软件工程' AS direction,
         '高级工程师｜408考研（计网、计组）、AI与软件工程；指导标书生成、论文检测、脱口派APP等项目' AS intro, 16 AS quota UNION ALL
  SELECT 'changlingling', '高级工程师', '操作系统、软件测试',
         '高级工程师｜操作系统、软件测试，侧重底层原理、软件质量保障', 16 UNION ALL
  SELECT 'liuguanjun', 'AI 全栈工程师', 'Vue/React、Node.js/Python、AI Agent、跨端混合开发',
         'AI全栈工程师｜Vue/React、Node.js/Python，主攻AI Agent、跨端混合开发', 16 UNION ALL
  SELECT 'fanjiahao', 'AI 全栈工程师', 'Java/SpringBoot、大型企业级项目',
         'AI全栈工程师｜Java/SpringBoot，大型企业级项目（移动、铁塔、联通相关）', 16 UNION ALL

  SELECT 'huguoxin', '主管', '安卓开发、Spring 框架、企业级应用开发',
         '主管｜安卓开发、Spring框架、企业级应用开发；参与诗达人、国网电力调度等项目', 22 UNION ALL
  SELECT 'wangwei', '教师', 'Spring、微服务、全栈、企业级开发',
         '教师｜Spring、微服务、全栈、企业级开发', 21 UNION ALL
  SELECT 'lihuanzhen', '教师', '软件测试、项目管理、UI 自动化、全栈开发',
         '教师｜软件测试、项目管理、UI自动化、全栈开发', 21 UNION ALL

  SELECT 'jiahaitao', '项目经理/Agent/全栈', '移动端、AI 全栈、Agent、AI 产品/项目管理',
         '项目经理/Agent/全栈｜10年研发经验，曾任职随锐、快乐沃克，冬奥会技术顾问；移动端、AI全栈、Agent、AI产品/项目经理', 16 UNION ALL
  SELECT 'pingyuxuan', '全栈开发工程师', 'Python、大模型、RAG、AI Agent',
         '全栈开发工程师｜10年项目实战，参与移动App商城、云听平台；Python、大模型、RAG、AI Agent', 16 UNION ALL
  SELECT 'zhangxiang', 'Java 后端/全栈', 'SpringBoot/SpringCloud、高并发、微服务、Linux 部署',
         'Java后端/全栈｜5年Java后端经验，精通SpringBoot/SpringCloud、MySQL、Redis、RabbitMQ；高并发、SQL优化、微服务、Linux部署', 16 UNION ALL
  SELECT 'wangxiaolong', '硬件/云服务器/网络运维', '网络工程、服务器运维、阿里云、域名备案',
         '硬件/云服务器/网络运维｜思科认证网络工程师，十余年运维经验；企业服务器、华为/思科网络设备、阿里云、域名备案', 16 UNION ALL

  SELECT 'gaoxuan', '团队带头人', '人工智能算法、智能体应用、知识库落地',
         '团队带头人｜人工智能算法、智能体应用、知识库落地；上线多款小程序；育人：做推进社会进步的研究，育敢为人先的人才', 14 UNION ALL
  SELECT 'wangpengfan', '高级 AI 全栈导师', 'Java 企业级开发、AI 全栈',
         '高级AI全栈导师｜深耕Java企业级开发、AI全栈；上线网络考试系统、村级事务平台等；育人：技术是迭代的，但底层逻辑和架构思维是永恒的', 13 UNION ALL
  SELECT 'zhanghuibin', '硬核架构师', 'AI 全栈、Java 开发、数据库',
         '硬核架构师｜AI全栈、Java开发、数据库；保险SaaS、智慧农业平台等项目；擅长竞赛指导、软著；育人：做有效沟通，对认真的人负责', 13 UNION ALL
  SELECT 'wangfengpei', 'AI 全栈导师', '前端工程化、企业级研发',
         'AI全栈导师｜前端工程化，企业级研发；智慧农业平台、AI家校平台等；育人：以企业真实需求为导向，提升工程化交付能力', 12 UNION ALL
  SELECT 'xuruize', 'AI 全栈执行导师', '产品设计方向、实训课主讲',
         'AI全栈执行导师｜产品设计方向，主讲实训课；考研督导、监管系统、实习管理平台；育人：以产品思维筑基，全心投入AI全栈育人，打造能做事的团队', 12 UNION ALL

  SELECT 'yangzhenying', '负责人', 'UE 互动开发（仿真、数字孪生、数字人、游戏）',
         '负责人｜UE互动开发（仿真、数字孪生、数字人、游戏）', 13 UNION ALL
  SELECT 'daiyifan', '游戏美术与开发', 'UE、三维建模、塔防/VR 游戏、数字人、虚拟制片、特效场景',
         '游戏美术与开发｜UE、三维建模、塔防/VR游戏、数字人、虚拟制片、特效场景', 10 UNION ALL
  SELECT 'maqingyu', 'AI 影视创作', '创意剧本、叙事、AI 短剧、AIGC 影视',
         'AI影视创作｜金牌编剧，创意剧本、叙事、AI短剧；AIGC影视，AI短剧单条播放百万', 13 UNION ALL
  SELECT 'pangkaiwen', 'AI 影视创作', '生成式视频、短视频、AIGC',
         'AI影视创作｜生成式视频、短视频、AIGC', 13 UNION ALL
  SELECT 'dingleilei', '人工智能', '机器学习、大模型、元学习、小样本学习',
         '人工智能｜机器学习、大模型、企业级项目；元学习、小样本学习，有百万级智慧农业等企业项目', 13 UNION ALL

  SELECT 'wanghao', '组长', 'AI 全栈工程',
         '组长｜AI全栈工程', 16 UNION ALL
  SELECT 'liujialiang', '导师', 'AI 全栈工程', 'AI全栈工程', 16 UNION ALL
  SELECT 'liutianmeng', '导师', 'AI 大模型方向', 'AI大模型方向', 16 UNION ALL
  SELECT 'lina', '导师', '需求与产品质量', '需求与产品质量', 16
) t
JOIN users u ON u.username = t.username
ON DUPLICATE KEY UPDATE
  remaining_quota = tutors.remaining_quota + VALUES(quota) - tutors.quota,
  quota = VALUES(quota),
  lab_id = VALUES(lab_id),
  title = VALUES(title),
  research_direction = VALUES(research_direction),
  intro = VALUES(intro),
  status = 1;

-- ------------------------------------------------------------
-- 四、实训室负责人（202 刘冠军 / 402 贾海涛 已由用户确认）
-- ------------------------------------------------------------
UPDATE labs l JOIN users u ON u.username = 'liuguanjun'  SET l.leader_id = u.id WHERE l.code = '202';
UPDATE labs l JOIN users u ON u.username = 'huguoxin'    SET l.leader_id = u.id WHERE l.code = '204';
UPDATE labs l JOIN users u ON u.username = 'jiahaitao'   SET l.leader_id = u.id WHERE l.code = '402';
UPDATE labs l JOIN users u ON u.username = 'gaoxuan'     SET l.leader_id = u.id WHERE l.code = '404';
UPDATE labs l JOIN users u ON u.username = 'yangzhenying' SET l.leader_id = u.id WHERE l.code = '406';
UPDATE labs l JOIN users u ON u.username = 'wanghao'     SET l.leader_id = u.id WHERE l.code = '505';
