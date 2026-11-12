# AI 能力接入说明

本项目的 AI 能力已经预留好接口、评分标准与教师分身身份数据。**填一把 Key 就能接入真实模型**，
支持同时配置多个通道（多把 Key）：某个通道额度用尽会自动切换到下一个，全部用尽时前端显示红色提示。

---

## 一、多通道接入

在根目录 `.env`（或服务器环境变量）里填任意一到多把 Key：

```bash
# 通道一
AI_OPENAI_KEY=sk-xxxx
AI_OPENAI_BASE_URL=https://api.openai.com/v1
AI_OPENAI_MODEL=gpt-4o-mini

# 通道二（额度备份）
AI_DEEPSEEK_KEY=sk-yyyy
AI_DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
AI_DEEPSEEK_MODEL=deepseek-chat

# 通道三
AI_QWEN_KEY=
AI_QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_QWEN_MODEL=qwen-plus

# 首选通道（留空则按 openai → deepseek → qwen 顺序）
AI_ACTIVE=
# 额度耗尽后的冷却时间（分钟）
AI_QUOTA_COOLDOWN_MINUTES=10
```

**切换策略**（`MultiProviderAiGateway`）：

1. 优先用 `AI_ACTIVE` 指定的通道，其余按配置顺序；
2. 命中额度类错误（HTTP 402/429、`insufficient_quota`、余额不足、限流）→ 标记该通道「额度不足」并冷却 N 分钟，**自动切下一个**；
3. 网络或格式错误同样尝试下一个，保证可用性；
4. 全部不可用 → 返回 `code=429`，前端在 AI 区块内显示红框，文案列出每个通道的失败原因；
5. 调用成功的通道自动成为「当前通道」，管理端状态与实际一致。

判断逻辑（`AiConfig`）：

| 条件 | 使用的实现 |
|---|---|
| 任一 `AI_*_KEY` 非空 | `MultiProviderAiGateway`（多通道，自动切换） |
| 只填了旧的 `AI_API_KEY` | 视为单通道，同样走多通道网关（一条通道） |
| 一把 Key 都没有 | `MockAiGateway`（内置规则引擎，结构一致） |
| `AI_PROVIDER=mock` | 强制规则引擎，用于离线演示 |
| `AI_ENABLED=false` | 熔断，全部走规则引擎 |

### 常见供应商

| 供应商 | BASE_URL | MODEL 示例 |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| 智谱 | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` |

其他参数：`AI_TIMEOUT_SECONDS`（默认 30）、`AI_JSON_MODE`（默认 true，个别网关不支持
`response_format=json_object` 时置 false）。

> 增加第四个通道：在 `application.yml` 的 `app.ai.providers` 里加一条即可，代码不用动。
> 本项目用的是 OpenAI 兼容的 `POST {base}/chat/completions`，不做流式输出。

### 额度不足时的表现

- 接口层：`HTTP 200` + `{"code":429,"message":"所有 AI 通道额度均不足…（openai：额度不足；deepseek：额度冷却中）"}`。
- 页面层：学生端「AI 实训室推荐」「AI 面试技巧」、导师端「AI 简历初评」、分身对话框内均显示**红色描边提示框**，
  标题为「AI 额度不足，已暂停分析」，不再弹一次性 toast，避免被忽略。
- 管理端：「全局看板 → AI 通道」实时显示每个通道的状态点、额度标记、冷却时间与切换按钮。

---

## 二、接口一览

所有接口都需要登录 Token（`Authorization: Bearer <token>`），响应统一为 `{code, message, data}`。
AI 类接口的 `data` 外层统一带：

```json
{
  "engine": "multi | mock",
  "provider": "deepseek（本次实际承担调用的通道）",
  "model": "deepseek-chat",
  "placeholder": true,
  "generatedAt": "2026-11-12T10:00:00",
  "data": { }
}
```

`placeholder=true` 表示当前是规则引擎结果，前端会显示「规则引擎预览」标签。

### 1. 评分标准（全端共用）

```
GET /api/ai/rubric
```

```json
[{
  "code": "MAJOR", "name": "专业基础", "weight": 25,
  "description": "核心课程掌握程度、专业概念是否成体系",
  "anchors": ["概念模糊，需提示才能作答", "基础概念正确，深度有限", "…", "…"]
}]
```

### 2. 学生端 · 面试技巧分析

```
POST /api/ai/interview-tips        （角色：STUDENT）
```

```json
{"data": {
  "summary": "一句话结论",
  "focus":   [{"code":"SKILL","name":"实践技能","weight":25,"focus":"本轮考察点"}],
  "questions":[{"question":"…","intent":"想验证什么","outline":["回答要点"]}],
  "tips":    [{"title":"用 STAR 讲项目","detail":"…"}],
  "selfCheck":["能 1 分钟讲清一个项目"]
}}
```

### 3. 学生端 · 实训室推荐

```
GET /api/ai/recommendations        （角色：STUDENT）
```

```json
{"data": {
  "summary": "排序依据说明",
  "items": [{
    "tutorId": 2, "tutorName": "张伟", "labName": "人工智能实训室",
    "researchDirection": "计算机视觉", "matchScore": 76,
    "skillHits": ["Java","算法设计"], "reasons": ["…"], "risks": ["…"],
    "suggestedRank": 1
  }]
}}
```

### 4. 学生端 · 导师 AI 分身

```
GET  /api/ai/avatars                       （角色：STUDENT）分身列表
POST /api/ai/avatars/{tutorId}/chat        （角色：STUDENT）
```

```json
// 请求
{"question": "方向主要做什么？", "history": [{"role":"student","content":"…"},{"role":"assistant","content":"…"}]}
// 响应
{"data": {"answer": "…", "citations": ["研究方向：…"], "boundary": "不承诺录取结果…"}}
```

### 5. 教师端 · 简历初评 + 多维评分建议

```
POST /api/ai/resume-review         （角色：TUTOR / LAB_ADMIN）
```

```json
// 请求
{"applicationId": 1}
// 响应 data
{
  "summary": {"headline":"…","suggestedTotal":79.9,"recommendation":"正常面试，重点核实技能"},
  "highlights": ["…"],
  "risks": ["…"],
  "suggestedQuestions": [{"question":"…","intent":"…","outline":["…"]}],
  "dimensions": [{"code":"MAJOR","name":"专业基础","weight":25,"score":91,"reason":"GPA 3.65 折算","confidence":"MEDIUM"}],
  "disclaimer": "仅作参考，不能替代导师判断"
}
```

> 导师只能查看报考自己的学生，越权返回 403。
> 入参里的 `resume` 包含文件元数据（是否上传、文件名、大小、时间）与**正文**：
> 正文由 PDF 文本层或 PaddleOCR 抽取（见第五节），字段 `resume.text` 最多 4000 字；
> 正文为空时模型只能按档案推断，不得虚构简历内容。

### 6. 教师端 · 保存多维评分

```
POST /api/workbench/applicants/{applicationId}/scores   （角色：TUTOR / LAB_ADMIN，仅面试阶段）
```

```json
{"items": [{"code":"MAJOR","score":90,"comment":"概念清楚"}, {"code":"SKILL","score":80}]}
```

后端按 `score_rubrics` 权重折算总分（`Σ(score×weight)/Σweight`），写入
`interview_results.score`，明细写入 `interview_scores`，`score_detail` 记录可读摘要。
维度缺失时该维度不参与折算；分数非法（<0 或 >100）直接报错。

### 7. 管理员 · 分身身份维护

```
GET  /api/ai/personas/{tutorId}    （角色：SYS_ADMIN）
PUT  /api/ai/personas/{tutorId}    （角色：SYS_ADMIN）
```

```json
{
  "displayName": "张伟老师助手",
  "tone": "严谨、耐心，像实验室的师兄",
  "expertise": "计算机视觉、目标检测",
  "boundaries": "不承诺录取结果、不评价其他导师、不透露面试题目",
  "greeting": "你好，我是张伟老师的 AI 分身…",
  "knowledge": "导师：张伟（副教授）\n实训室：人工智能实训室\n…",
  "model": "deepseek-chat",
  "status": 1
}
```

不填时系统按导师资料自动推导（`source=derived`），一旦 PUT 过就以库里为准（`source=custom`）。

### 8. 管理员 · AI 通道状态与切换

```
GET  /api/ai/providers                 （角色：SYS_ADMIN）
POST /api/ai/providers/{name}/activate （角色：SYS_ADMIN，切换首选并清除该通道冷却）
```

```json
[{
  "name": "openai", "model": "gpt-4o-mini",
  "keyMasked": "sk-t****st-a",
  "configured": true, "active": false, "available": false,
  "quotaExceeded": true, "cooldownUntil": "2026-11-12T10:23:23",
  "lastError": "「openai」额度不足或已限流：…",
  "successCount": 12, "failureCount": 1
}]
```

`available=false` 表示该通道正在冷却或未配置 Key；`quotaExceeded=true` 会显示红色「额度不足」标签。

---

## 三、评分标准（面试）

五个维度，权重合计 100，落在 `score_rubrics` 表，前端滑杆与 AI 建议共用这一份数据。

| 编码 | 维度 | 权重 | 考察点 |
|---|---|---|---|
| `MAJOR` | 专业基础 | 25 | 核心课程掌握程度、专业概念是否成体系 |
| `SKILL` | 实践技能 | 25 | 项目经历、代码与工具链的动手能力 |
| `POTENTIAL` | 学习潜力 | 20 | 自学能力、调试思路、对未知问题的拆解 |
| `MOTIVE` | 动机匹配 | 15 | 对本实训室方向的了解程度与投入意愿 |
| `COMM` | 表达沟通 | 15 | 逻辑结构、倾听与协作意识 |

每个维度带四段行为锚点（0–59 / 60–74 / 75–89 / 90–100），例如「实践技能」：

1. 仅课程作业，无独立实现
2. 能按文档完成模块
3. 独立完成过完整模块并有踩坑记录
4. 有可演示的完整项目与性能/质量取舍

调权重或锚点直接改 `score_rubrics`（`status=0` 可停用某维度），前后端与 AI 自动跟随。
历史评分明细保留在 `interview_scores`，便于复盘口径。

---

## 四、数据表

| 表 | 作用 |
|---|---|
| `score_rubrics` | 评分维度、权重、行为锚点（标准唯一来源） |
| `ai_personas` | 教师 AI 分身身份设定（未配置则按导师资料推导） |
| `interview_scores` | 多维评分明细（与 `interview_results.score` 汇总分并存） |
| `ai_reviews` | 每次 AI 调用的入参摘要与输出，留痕可追溯 |
| `resume_texts` | 简历正文抽取缓存（文本层 / PaddleOCR 结果，重新上传即失效） |

`ai_reviews` 建议定期归档：`kind` 区分四类能力，`application_id` 可回溯某次面试评估。

---

## 五、简历正文抽取（PDF 文本层 + PP-StructureV3）

教师端「AI 简历初评」不再只看档案：会先把 PDF 简历转成正文，再交给模型。
扫描件/图片版走 **PP-StructureV3**（PaddleOCR 3.x 的文档解析流水线），拿到的不是一串杂乱的 OCR 文本，
而是带标题层级、列表、表格的 **Markdown**，简历结构不会被揉乱。

### 抽取顺序

1. **PDF 文本层**（`PDFTextStripper`）：绝大多数导出型简历都有文本层，准确、快、零外部依赖；
2. 文本层字数 < `OCR_MIN_CHARS`（默认 60）→ **PP-StructureV3**：整份 PDF 以 base64 直接提交
   （`fileType=0`，服务端自己拆页），返回 `layoutParsingResults[].markdown.text`；
3. PP-StructureV3 不可用/报错 → 降级到**普通 OCR**（PDFBox 渲染 PNG，逐页识别），未配则保留文本层；
4. **DOCX** 用 POI 直读段落与表格；`.doc` 暂不支持，会标注「格式暂不支持」；
5. 结果写入 `resume_texts` 缓存，同一份简历再次初评不再重复解析，**重新上传即失效**。

> 为什么不让所有 PDF 都过一遍解析：版面分析是秒级、吃 GPU/CPU 的活，
> 有文本层的 PDF 直接读既准又快，没必要为「结构化」付这个成本。

### 接入 PP-StructureV3

```bash
# 本地服务（PaddleX 3.x）
paddlex --serve --pipeline PP-StructureV3 --port 8080
# OCR_STRUCTURE_ENDPOINT=http://127.0.0.1:8080/layout-parsing

# 星河社区在线服务（无需本地 GPU，按量计费）
# OCR_STRUCTURE_ENDPOINT=https://xxxx.aistudio-app.com/layout-parsing
# OCR_STRUCTURE_API_KEY=<访问令牌>
```

请求体：

```json
{"file": "<base64>", "fileType": 0, "useTableRecognition": true,
 "useFormulaRecognition": false, "useDocOrientationClassify": false, "useDocUnwarping": false}
```

`fileType`：`0`=PDF、`1`=图片。鉴权默认按 `Authorization: token <key>` 发送
（`OCR_STRUCTURE_AUTH_SCHEME` 可改为 `Bearer` 或 `none`）。

响应兼容：

| 形态 | 取值路径 |
|---|---|
| PP-StructureV3 / PaddleX | `result.layoutParsingResults[].markdown.text` |
| 仅版面结果 | `result.layoutParsingResults[].prunedResult.parsing_res_list[].block_content` |
| 纯识别兜底 | `prunedResult.overall_ocr_res.rec_texts[]`、`texts[]` |
| 业务错误 | `errorCode != 0` → 502 `文档解析失败：<errorMsg>` |

### 配置项

| 变量 | 默认 | 说明 |
|---|---|---|
| `OCR_STRUCTURE_ENDPOINT` | 空 | PP-StructureV3 地址，留空则不启用文档解析 |
| `OCR_STRUCTURE_API_KEY` | 空 | 在线服务令牌；留空则复用 `PADDLEOCR_API_KEY` |
| `OCR_STRUCTURE_AUTH_SCHEME` | `token` | `token` / `Bearer` / `none` |
| `OCR_STRUCTURE_TABLE` | `true` | 识别表格（简历里的成绩单、项目表） |
| `OCR_STRUCTURE_FORMULA` | `false` | 识别公式，简历一般不需要，开了更慢 |
| `OCR_STRUCTURE_TIMEOUT_SECONDS` | 120 | 整份 PDF 逐页版面分析，超时单独放宽 |
| `PADDLEOCR_ENDPOINT` | 空 | 普通 OCR 兜底地址 |
| `PADDLEOCR_API_KEY` | 空 | 普通 OCR 鉴权，按 Bearer 发送 |
| `OCR_PROVIDER` | `auto` | `auto` 先解析再兜底；`pp-structure-v3` / `ocr` 只用其一；`none` 全关 |
| `OCR_MIN_CHARS` | 60 | 文本层少于该字数才触发解析 |
| `OCR_MAX_PAGES` | 5 | 普通 OCR 兜底时的渲染页数上限 |
| `OCR_RENDER_DPI` | 150 | 普通 OCR 兜底的渲染精度 |

### 返回给前端的解析状态

初评结果里的 `resumeDigest` 会说明正文是否可用，导师端据此显示标签：

```json
{"engine": "pdf-text | pp-structure-v3 | paddleocr | docx-text | none | encrypted | failed",
 "chars": 428, "pages": 1, "used": true, "cached": false}
```

- `used=true` → 标签显示「已读简历正文 428 字 · PP-StructureV3 文档解析」
- 未上传 / 解析失败 / 加密 PDF → 显示对应原因，初评退回按档案推断，并在风险里提示人工核对
- 解析服务报错不会让初评失败：记一条 warn 日志后自动降级

> 边界：解析结果可能有识别噪声，提示词要求模型「引用正文要点作为依据」，不要求逐字复述；
> 简历正文只在服务端参与推理，不写进 `ai_reviews` 的入参摘要之外的其他表。

---

## 六、扩展方式

- **换模型供应商**：实现 `AiGateway`（四个方法 + provider/model），在 `AiConfig` 中按
  `properties.getProvider()` 返回你的实现。业务代码、前端、数据库都不用动。
- **加新能力**（如简历解析 PDF、面试语音转写）：在 `AiGateway` 加方法 + `AiService` 组装数据 +
  `AiController` 暴露接口，输出结构保持 `Map` 即可复用 `ai_reviews` 留痕。
- **调整提示词**：`AiProperties.systemPrompt` 是全局约束（不编造名额、不承诺录取、不透露题目），
  各能力的任务提示词在 `OpenAiCompatibleGateway` 顶部的 `SCHEMA_*` 常量里。

---

## 七、注意事项

1. **Key 不要进仓库**：`.env` 已在 `.gitignore` 中，生产用环境变量注入。
2. **超时**：默认 30 秒，超时会返回 502 并在前端提示；建议给网关设置总并发与限流。
3. **成本**：简历初评与面试分析按次调用，一次面试约 5 名候选 → 单轮约 5 次调用，量可控。
4. **合规**：AI 结果只作参考，`disclaimer` 会随结果展示；分身回答受 `boundaries` 约束，
   不承诺录取、不评价他人、不泄露题目。
5. **降级**：单个通道额度耗尽会自动切换；全部不可用时接口返回 `code=429` 并在页面显示红框，
   不会返回假数据。如需完全离线，可把 `AI_ENABLED=false` 切回规则引擎，接口结构不变。
