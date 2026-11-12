# 学生实训室双向选择系统

学院实训室选组选拔的全流程线上系统：学生填平行志愿，导师面试打分并锁定名额，实训室统筹排期，学院控制时间轴与兜底调剂。

**技术栈**：Vue 3 + Element Plus + Vite 5 / Spring Boot 3.5 + MyBatis-Plus + MySQL 8 / JWT。

---

## 1. 三端结构（四类账号，三个登录入口）

登录页分为**学生端 / 导师端 / 管理员端**三个入口，账号与入口强绑定：登录时提交 `portal`，后端校验 `users.role` 是否属于该入口，走错入口直接拒绝（403），不会串到其他端。登录成功后返回能力清单，前端按能力渲染菜单，后端按角色做二次校验。

| 登录入口 | 角色 | 工作台 | 能力 |
|---|---|---|---|
| 学生端 | `STUDENT` | 学生端 | 导师与方向、3 个平行志愿、**简历上传**、Offer 确认、录取结果 |
| 导师端 | `TUTOR` / `LAB_ADMIN` | **同一个工作台** | 导师可见「报考我的学生、面试打分、拟录取/递补/淘汰」；实训室管理员额外可见「本室进度、面试排期、室内调剂」 |
| 管理员端 | `SYS_ADMIN` | 管理端 | 阶段与轮次、导师与实训室、全局看板、统筹调剂、结果导出 |

说明：
- 数据库保留 `STUDENT / TUTOR / LAB_ADMIN / SYS_ADMIN` 四种角色，**实训室负责人（`LAB_ADMIN`）与导师端合并为同一个入口**，菜单按 `caps` 渲染。
- 「实训室负责人」由管理员在 **导师与实训室 → 编辑导师 → 授予实训室负责人身份** 中切换：授予后 `users.role` 变为 `LAB_ADMIN`、`labs.leader_id` 指向本人，导师端多出「实训室事务」；撤销后回退为 `TUTOR`。每个实训室仅一位负责人，改派需先取消原负责人。
- 一个账号若同时是导师（有 `tutors` 记录）又是实训室管理员（`role=LAB_ADMIN`），登录后两套能力都开放，菜单同时出现「导师事务」与「实训室事务」。

## 2. 快速启动

前置：JDK 17+、Maven 3.6+、Node 18+、MySQL 8（服务已启动）。

```bash
# 1) 配置（复制后填入你的 MySQL 密码）
cp .env.example .env

# 2) 一键启动前后端（Git Bash / WSL / macOS / Linux）
./scripts/dev.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
```

启动后：前端 <http://localhost:8089>，后端 <http://localhost:8080>，健康检查 `/api/health`。

首次启动会自动建表（`schema.sql`）并写入演示数据；数据库不存在时会自动创建（`createDatabaseIfNotExist=true`，默认库名 `lab_select_v2`）。

### 手动启动

```bash
# 后端
cd backend && mvn spring-boot:run
# 或打包运行
mvn -DskipTests package && java -jar target/lab-select.jar

# 前端
cd frontend && npm install && npm run dev -- --port 8089
```

后端读取的环境变量：`DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD`、`JWT_SECRET`、`DEMO_DATA`。
`scripts/dev.sh` 会把 `.env` 里的 `MYSQL_*` 映射成这套变量。

### Docker 部署

```bash
cp .env.example .env        # 填 MYSQL_ROOT_PASSWORD，按需改 WEB_PORT / API_PORT
docker compose up -d --build
docker compose ps           # 等 mysql、backend 都变成 healthy
```

访问 <http://localhost:8089>（前端 Nginx，`/api` 已反向代理到后端）。后端也单独暴露在 <http://localhost:8080>，方便调接口。

| 容器 | 镜像 | 端口 | 说明 |
|---|---|---|---|
| `labselect-mysql` | `mysql:8.0` | `${MYSQL_PORT:-3306}` | 数据持久化到命名卷 `labselect-mysql-data` |
| `labselect-backend` | 本地构建 `labselect-backend:2.0.0` | `${API_PORT:-8080}` | 多阶段构建（Maven → JRE 17），非 root 运行，带 healthcheck |
| `labselect-frontend` | 本地构建 `labselect-frontend:2.0.0` | `${WEB_PORT:-8089}` → 80 | 多阶段构建（Node 20 → Nginx），处理 SPA history 回退与静态缓存 |

启动顺序由健康检查保证：`mysql healthy → backend healthy → frontend`；后端启动时自动建库建表并写入演示数据。

常用运维：

```bash
docker compose logs -f backend     # 看后端日志
docker compose restart backend     # 改完配置重启
docker compose up -d --build       # 改代码后重建镜像
docker compose down                # 停止，数据保留
docker compose down -v             # 连数据卷一起删，下次启动重新初始化演示数据
```

注意事项：
- 构建阶段需要联网拉取 Maven / npm 依赖，`NPM_REGISTRY` 可在 `.env` 里换源。
- 宿主机 3306/8080/8089 被占用时，改 `.env` 里的三个端口即可。
- 若本机已有 MySQL 占用 3306，可把 `MYSQL_PORT` 改成 3307（容器内仍是 3306，不影响后端连接）。

## 3. 演示账号

密码统一 `123456`。登录时先选对应入口，再填账号密码。

| 账号 | 姓名 | 身份 | 登录入口 | 能看到什么 |
|---|---|---|---|---|
| `admin` | 张建国 | 系统管理员 | 管理员端 | 管理端全部功能 |
| `liuguanjun` | 刘冠军 | 202 实训室负责人 兼 导师 | 导师端 | 导师事务 + 实训室事务（合并工作台） |
| `huguoxin` | 胡国欣 | 204 实训室负责人 兼 导师 | 导师端 | 同上 |
| `jiahaitao` | 贾海涛 | 402 实训室负责人 兼 导师 | 导师端 | 同上 |
| `gaoxuan` | 高璇 | 404 实训室负责人 兼 导师 | 导师端 | 同上 |
| `yangzhenying` | 杨振樱 | 406 实训室负责人 兼 导师 | 导师端 | 同上 |
| `wanghao` | 王浩 | 505 实训室负责人 兼 导师 | 导师端 | 同上 |
| 其余 19 位导师 | — | 导师 | 导师端 | 只有导师事务，账号为姓名全拼（如 `zhanghuibin`） |
| `2023001` | 林晓 | 学生 | 学生端 | 空志愿，用于演示完整填报 |
| `2023002` ~ `2023008` | — | 学生 | 学生端 | 已预填志愿 |

## 4. 八个业务阶段

阶段全部落在 `selection_phases` 表，**时间窗口是唯一权威**：所有写接口第一步校验当前时间是否在窗口内，抓包直接调接口同样会被拒绝。

| 序号 | 阶段 | 轮次 | 写操作 |
|---|---|---|---|
| 1 | 宣讲 | 全局 | 只读 |
| 2 | 开放日 | 全局 | 只读 |
| 3 | 意向填报 | 1~3 | 提交/修改 3 个平行志愿、调剂意愿 |
| 4 | 面试与初选 | 1~3 | 导师打分、拟录取（占名额）、递补、淘汰 |
| 5 | 拟录取确认 | 1~3 | 学生确认/放弃；超时自动放弃 + 顺延递补 |
| 6 | 分轮补录 | 全局（覆盖 2、3 轮） | 重复 3~5 |
| 7 | 统筹调剂 | 全局 | 室内调剂 → 全院调剂 → 人工兜底 |
| 8 | 结果公示 | 全局 | 只读 + 导出 |

演示时可在「阶段与轮次」页对任一阶段点 **立即进入**（窗口平移到当前时间、保留时长）或 **立即结束**，用来快速走完 3→4→5→7 全流程。

## 5. 关键实现

| 问题 | 做法 |
|---|---|
| 时间强控 | `PhaseService.require(...)` 统一校验，异常信息带阶段名与开放时间 |
| 名额防超卖 | `UPDATE tutors SET remaining_quota = remaining_quota - 1 WHERE id = ? AND remaining_quota > 0`，受影响行数必须为 1 |
| 超时自动递补 | 定时任务每 30 秒扫描到期 `confirm_status=0` 的 Offer：置超时 → 释放名额 → 顺位最小递补转拟录取 → 发通知 |
| 单 Offer 自动确认 | 确认阶段内，只收到 1 个待确认 Offer 的学生由定时任务自动确认 |
| 一人一轮一个 Offer | 确认时作废同轮其他 Offer 并释放名额、触发递补 |
| 三级调剂 | `AdjustService`：按 `priority_score` 降序，先匹配填报过的实训室，再全院，最后人工 |
| 分端登录 | `POST /api/auth/login` 携带 `portal`（`STUDENT` / `TUTOR` / `ADMIN`），与 `users.role` 不匹配返回 403；成功返回 `{token, user:{role, caps, labId, tutorId}, phase}` |
| AI 能力 | 四类能力走 `AiGateway` 单一接入点，**支持多通道（多把 Key）**：某个通道额度耗尽自动切换下一个，全部耗尽时前端显示红色提示，管理端「全局看板 → AI 通道」可查看状态并手动切换；未配置 Key 时自动回落内置规则引擎（结构一致）。评分标准落在 `score_rubrics`，教师分身身份落在 `ai_personas`。详见 `docs/AI集成说明.md` |
| 简历存储 | 学生上传 PDF/DOC/DOCX（≤10MB），落盘 `backend/uploads/resumes`（已 gitignore），元数据存 `student_resumes`；下载走鉴权接口，导师仅能查看报考自己的学生。AI 初评会先抽 PDF 文本层，扫描件走 **PP-StructureV3** 文档解析（`OCR_STRUCTURE_ENDPOINT`，返回结构化 Markdown），失败再降级普通 OCR；正文缓存在 `resume_texts`，重新上传即失效 |

## 6. 目录结构

```
system/
├── docker-compose.yml               # 三个容器：mysql / backend / frontend
├── .env.example                     # 端口、数据库密码、演示开关
├── backend/
│   ├── Dockerfile                   # 多阶段构建（Maven → JRE 17，非 root 运行）
│   └── src/main/
│       ├── java/com/hbsd/labselect/
│       │   ├── common/              R、异常、JWT、鉴权拦截器、@RequireRole
│       │   ├── config/              Web / Jackson / Password 配置
│       │   ├── entity/              12 张表对应实体
│       │   ├── mapper/              MyBatis-Plus Mapper（含名额原子扣减 SQL）
│       │   ├── service/             Phase / Auth / Student / Workbench / Offer / Adjust / Admin
│       │   ├── controller/          Auth / Meta / Student / Workbench / Admin / Notification
│       │   ├── scheduler/           Offer 超时与自动确认
│       │   └── init/                演示数据初始化
│       └── resources/schema.sql     建表脚本（12 张表）
├── frontend/
│   ├── Dockerfile                   # Node 20 构建 → Nginx 托管
│   ├── nginx.conf                   # SPA history 回退 + /api 反向代理
│   └── src/
│       ├── layout/                  MainLayout（按 caps 渲染菜单 + 阶段倒计时）
│       ├── views/student/           导师与方向、我的志愿、我的简历、我的 Offer、录取结果
│       ├── views/workbench/         报考我的学生、我的录取、排期、本室进度、室内调剂
│       ├── views/admin/             全局看板、阶段与轮次、导师与实训室、统筹调剂、公示
│       ├── api/ router/ stores/ utils/
│       └── styles/
├── docs/启动指南.md
├── docs/AI集成说明.md
└── 需求文档.md
```

## 7. 接口一览（简）

```
POST /api/auth/login                      分端登录（portal：STUDENT / TUTOR / ADMIN）
GET  /api/meta/phase|tutors|labs          阶段与公开信息

GET  /api/student/tutors|application|offers|result
PUT  /api/student/application             提交/修改平行志愿
POST /api/student/offers/{id}/confirm     确认 Offer
POST /api/student/offers/{id}/decline     放弃 Offer

GET/POST/DELETE /api/student/resume       简历元数据 / 上传（PDF·DOC·DOCX ≤10MB）/ 删除
GET  /api/student/resume/file             本人下载（鉴权，不暴露静态路径）

GET  /api/workbench/applicants|offers|overview
POST /api/workbench/applicants/{id}/score       录入成绩
POST /api/workbench/applicants/{id}/decision    拟录取/递补/淘汰
GET  /api/workbench/applicants/{id}/resume      查看该学生简历（需是其志愿导师）
GET/POST/PUT/DELETE /api/workbench/schedules    面试排期（室管）
GET  /api/workbench/lab/progress|students|pool|vacancies
POST /api/workbench/lab/adjust                  室内调剂

GET  /api/admin/overview|phases|tutors|labs|students|results
PUT  /api/admin/phases/{id}                     改时间窗口
POST /api/admin/phases/{id}/jump|close          立即进入 / 立即结束
POST /api/admin/rounds/{round}                  切换当前轮次
POST /api/admin/adjust/run|manual               自动 / 人工调剂
GET  /api/admin/results/export                  导出 Excel

# AI（未配置 Key 时为规则引擎占位，结构一致）
GET  /api/ai/rubric                             评分标准（维度/权重/锚点）
POST /api/ai/interview-tips                     学生：面试技巧分析
GET  /api/ai/recommendations                    学生：实训室推荐
GET  /api/ai/avatars                            学生：导师分身列表
POST /api/ai/avatars/{tutorId}/chat             学生：向分身提问
POST /api/ai/resume-review                      导师：简历初评 + 评分建议
POST /api/workbench/applicants/{id}/scores      导师：保存多维评分（折算总分）
GET/PUT /api/ai/personas/{tutorId}              管理员：分身身份设定
```

## 8. 注意

- 生产部署前：`JWT_SECRET` 换成 32 位以上随机串、`DEMO_DATA=false`、数据库改用独立业务账号。
- 演示数据的阶段窗口以「首次启动当天」为基准生成；跨天后窗口不会自动平移，可在管理端调整或重置演示库。
- `selection_phases` 的 `(code, round_no)` 唯一；全局阶段的 `round_no` 为 `NULL`。
