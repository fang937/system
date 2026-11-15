<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>AI 面试辅导</h2>
        <p>
          选择面试环节开始练习：先出题、再作答，提交后给出评分维度、亮点、改进建议与参考表达。练习记录仅本人可见。
        </p>
      </div>
      <el-tag effect="plain" size="large">不预测录取结果</el-tag>
    </div>

    <div class="stage-grid">
      <button
        v-for="stage in STAGES"
        :key="stage.key"
        type="button"
        class="stage-card"
        :class="{ active: section === stage.key }"
        @click="switchSection(stage.key)"
      >
        <span class="stage-icon">
          <el-icon :size="18"><component :is="stage.icon" /></el-icon>
        </span>
        <span class="stage-main">
          <span class="stage-title">{{ stage.title }}</span>
          <span class="stage-desc">{{ stage.desc }}</span>
        </span>
        <span class="stage-meta">{{ stage.meta }}</span>
      </button>
    </div>

    <div class="coach-layout">
      <div class="coach-main">
        <section ref="coachRef" class="ai-card coach-card">
          <div class="ai-head">
            <div class="ai-mark">AI</div>
            <div>
              <div class="ai-title">{{ currentStage.title }}</div>
              <div class="ai-meta">{{ currentStage.meta }}</div>
            </div>
            <span v-if="engine" class="ai-badge">{{ engine }}</span>
            <div class="head-gap" />
            <el-button size="small" :loading="loadingQuestion" @click="newQuestion()">
              {{ question ? '换一题' : '开始练习' }}
            </el-button>
          </div>

          <div v-if="!question && !loadingQuestion && !error" class="coach-empty">
            <div class="empty-icon">
              <el-icon :size="26"><Microphone /></el-icon>
            </div>
            <div class="empty-title">准备好就开始</div>
            <p>
              选择环节后点击「开始练习」，AI 会出一道题。写完回答提交，会得到评分、亮点、改进与参考表达。
            </p>
            <el-button type="primary" @click="newQuestion()">开始练习</el-button>
          </div>

          <div v-if="loadingQuestion" class="ai-loading">
            <span class="ai-dots"><i /><i /><i /></span>
            正在准备题目…
          </div>

          <div v-if="error" class="ai-alert">
            <el-icon class="ai-alert-icon"><WarningFilled /></el-icon>
            <div>
              <div class="ai-alert-title">{{ error.title }}</div>
              <div class="ai-alert-sub">{{ error.message }}</div>
            </div>
          </div>

          <template v-if="question">
            <div class="question-card">
              <div class="q-label">第 {{ questionNo }} 题 · {{ currentStage.title }}</div>
              <div class="q-text">{{ question.question }}</div>
              <div v-if="question.intent" class="q-intent">考察点：{{ question.intent }}</div>
              <div v-if="question.tips?.length" class="q-tips">
                <span class="q-tips-label">回答提示</span>
                <span v-for="tip in question.tips" :key="tip" class="q-tip">{{ tip }}</span>
              </div>
              <div class="q-foot">
                <span class="tiny">建议时长 {{ formatSeconds(question.timeLimitSeconds) }}</span>
                <span v-if="!feedback" class="tiny">已用时 {{ elapsedText }}</span>
              </div>
            </div>

            <div v-if="!feedback" class="answer-block">
              <el-input
                ref="answerRef"
                v-model="answer"
                type="textarea"
                :autosize="{ minRows: 6, maxRows: 14 }"
                placeholder="写下你的回答……建议用「背景 - 任务 - 行动 - 结果」组织，控制在 90 秒内。"
              />
              <div class="answer-foot">
                <span class="tiny" :class="{ 'over-text': answerOver }">
                  {{ answer.length }} / {{ MAX_ANSWER }}
                </span>
                <span class="tiny">至少 {{ MIN_ANSWER }} 字</span>
                <div class="answer-gap" />
                <el-button :disabled="submitting" @click="newQuestion()">换一题</el-button>
                <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitAnswer">
                  提交回答
                </el-button>
              </div>
              <p v-if="answer.length > 0 && answer.trim().length < MIN_ANSWER" class="input-hint">
                回答太短，至少写 {{ MIN_ANSWER }} 个字再提交。
              </p>
              <p v-if="answerOver" class="input-hint">回答超过 {{ MAX_ANSWER }} 字，请精简后再提交。</p>
            </div>

            <div v-else ref="feedbackAnchor">
              <CoachFeedback :feedback="feedback">
                <template #actions>
                  <el-button size="small" @click="retryQuestion">重答本题</el-button>
                  <el-button size="small" type="primary" @click="nextFromFeedback">下一题</el-button>
                </template>
                <template #followup>
                  <el-button size="small" @click="practiceFollowUp">练这道追问</el-button>
                </template>
              </CoachFeedback>
            </div>
          </template>
        </section>
      </div>

      <aside class="coach-side">
        <section class="card card-pad">
          <div class="section-title">
            <h3>练习设置</h3>
          </div>
          <div class="field">
            <div class="field-label">目标实训室 / 导师</div>
            <el-select
              v-model="tutorId"
              clearable
              filterable
              placeholder="选择志愿导师（可选）"
              style="width: 100%"
              @change="onTutorChange"
            >
              <el-option
                v-for="tutor in tutorOptions"
                :key="tutor.tutorId"
                :label="`${tutor.tutorName} · ${tutor.labName}`"
                :value="tutor.tutorId"
              />
            </el-select>
            <p class="tiny">岗位相关问题会结合所选导师的方向生成。</p>
          </div>
          <div class="field">
            <div class="field-label">目标岗位 / 方向</div>
            <el-input v-model="role" maxlength="60" placeholder="如：后端开发 / 计算机视觉" />
          </div>
          <p class="ai-note">练习只用于改进表达，不评价人品，也不预测录取结果。</p>
        </section>

        <section class="card card-pad">
          <div class="section-title">
            <h3>最近练习</h3>
            <span class="tiny">{{ history.length }} 条</span>
          </div>
          <div v-if="history.length" class="history-list">
            <button
              v-for="item in history"
              :key="item.id"
              type="button"
              class="history-item"
              @click="openHistory(item)"
            >
              <div class="h-top">
                <span class="h-section">{{ item.sectionTitle }}</span>
                <span class="h-score">{{ item.score ?? '—' }}</span>
              </div>
              <div class="h-question">{{ item.question }}</div>
              <div class="h-time">{{ fmtTime(item.createdAt) }}</div>
            </button>
          </div>
          <p v-else class="tiny">还没有练习记录。完成一次作答后会自动保存。</p>
        </section>
      </aside>
    </div>

    <el-drawer v-model="historyOpen" title="练习回顾" :size="historyDrawerSize">
      <div v-if="historyDetail" class="history-detail">
        <div class="hd-meta">
          {{ historyDetail.sectionTitle }} · {{ fmtTime(historyDetail.createdAt) }}
          <span v-if="historyDetail.tutorName"> · {{ historyDetail.tutorName }}</span>
        </div>
        <div class="hd-block">
          <div class="hd-label">题目</div>
          <p>{{ historyDetail.question }}</p>
        </div>
        <div class="hd-block">
          <div class="hd-label">我的回答</div>
          <p class="hd-answer">{{ historyDetail.answer }}</p>
        </div>
        <CoachFeedback v-if="historyDetail.feedback" :feedback="historyDetail.feedback" compact />
        <p v-else class="tiny">这条记录没有反馈数据。</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/index.js'
import { fmtTime } from '../../utils/labels.js'
import CoachFeedback from '../../components/CoachFeedback.vue'

const MAX_ANSWER = 2000
const MIN_ANSWER = 10

const STAGES = [
  {
    key: 'self-intro',
    title: '自我介绍',
    desc: '1 分钟讲清我是谁、能做什么、为什么来',
    meta: '表达结构 · 自我定位',
    icon: 'User'
  },
  {
    key: 'common',
    title: '常见问题演练',
    desc: '行为面与动机类问题，练习 STAR 结构',
    meta: '沟通表达 · 行为风格',
    icon: 'ChatLineSquare'
  },
  {
    key: 'role',
    title: '岗位相关问题模拟',
    desc: '结合目标实训室方向，练技术与方向匹配',
    meta: '方向理解 · 项目相关性',
    icon: 'Aim'
  }
]

const section = ref('self-intro')
const tutorId = ref(null)
const role = ref('')
const tutorOptions = ref([])
const question = ref(null)
const questionNo = ref(0)
const variant = ref(0)
const answer = ref('')
const feedback = ref(null)
const engine = ref('')
const loadingQuestion = ref(false)
const submitting = ref(false)
const error = ref(null)
const history = ref([])
const historyOpen = ref(false)
const historyDetail = ref(null)
const elapsed = ref(0)
const coachRef = ref(null)
const answerRef = ref(null)
const feedbackAnchor = ref(null)

let timer = null
let requestSeq = 0

const currentStage = computed(
  () => STAGES.find((stage) => stage.key === section.value) || STAGES[0]
)

const answerOver = computed(() => answer.value.length > MAX_ANSWER)

const canSubmit = computed(
  () =>
    Boolean(question.value) &&
    !submitting.value &&
    answer.value.trim().length >= MIN_ANSWER &&
    !answerOver.value
)

const elapsedText = computed(() => {
  const minutes = Math.floor(elapsed.value / 60)
  const seconds = elapsed.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

const historyDrawerSize = computed(() => (window.innerWidth <= 560 ? '92%' : '480px'))

function startTimer() {
  stopTimer()
  elapsed.value = 0
  timer = setInterval(() => {
    elapsed.value += 1
  }, 1000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function formatSeconds(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value <= 0) return '90 秒'
  return value >= 60 ? `${Math.round(value / 60)} 分钟` : `${value} 秒`
}

function engineLabel(res) {
  if (!res) return ''
  if (res.placeholder) return '规则引擎预览'
  return res.provider ? `${res.provider} · ${res.model}` : String(res.model || '模型')
}

function resetSession() {
  requestSeq += 1
  stopTimer()
  question.value = null
  feedback.value = null
  answer.value = ''
  error.value = null
  questionNo.value = 0
  variant.value = 0
  loadingQuestion.value = false
  submitting.value = false
}

function switchSection(key) {
  if (section.value === key) return
  section.value = key
  resetSession()
}

function onTutorChange() {
  const selected = tutorOptions.value.find((tutor) => tutor.tutorId === tutorId.value)
  if (selected?.researchDirection && !role.value.trim()) {
    role.value = selected.researchDirection
  }
}

async function newQuestion(force = false) {
  if (navigator.onLine === false) {
    error.value = offlineError('出题')
    return
  }
  if (!force && answer.value.trim() && !feedback.value) {
    try {
      await ElMessageBox.confirm('换题会清空当前作答，确认继续？', '换一题', {
        confirmButtonText: '换题',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  loadingQuestion.value = true
  error.value = null
  const seq = ++requestSeq
  try {
    variant.value += 1
    const res = await api.aiCoachQuestion({
      section: section.value,
      tutorId: tutorId.value,
      role: role.value,
      variant: variant.value
    })
    if (seq !== requestSeq) return
    const data = res?.data || {}
    if (!data.question) {
      error.value = {
        title: '本次没有生成题目',
        message: 'AI 返回了空题目，请点击「开始练习」重试。'
      }
      return
    }
    question.value = data
    questionNo.value += 1
    feedback.value = null
    answer.value = ''
    engine.value = engineLabel(res)
    startTimer()
    await nextTick()
    coachRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    if (seq !== requestSeq) return
    error.value = toError(e, '出题')
  } finally {
    if (seq === requestSeq) loadingQuestion.value = false
  }
}

async function submitAnswer() {
  if (!canSubmit.value) return
  if (navigator.onLine === false) {
    error.value = offlineError('提交')
    return
  }
  submitting.value = true
  error.value = null
  stopTimer()
  const seq = requestSeq
  try {
    const res = await api.aiCoachFeedback({
      section: section.value,
      tutorId: tutorId.value,
      role: role.value,
      question: question.value.question,
      questionIntent: question.value.intent,
      answer: answer.value.trim(),
      variant: variant.value
    })
    if (seq !== requestSeq) return
    const data = res?.data || {}
    if (data.score === undefined || data.score === null) {
      error.value = {
        title: '本次没有生成反馈',
        message: 'AI 返回的反馈为空，你的回答仍保留在页面，可直接重试。'
      }
      startTimer()
      return
    }
    feedback.value = data
    engine.value = engineLabel(res)
    await loadHistory()
    await nextTick()
    feedbackAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  } catch (e) {
    if (seq !== requestSeq) return
    error.value = toError(e, '反馈')
    startTimer()
  } finally {
    if (seq === requestSeq) submitting.value = false
  }
}

function retryQuestion() {
  feedback.value = null
  error.value = null
  startTimer()
  nextTick(() => answerRef.value?.focus())
}

function nextFromFeedback() {
  return newQuestion(true)
}

function practiceFollowUp() {
  if (!feedback.value?.followUp) return
  requestSeq += 1
  loadingQuestion.value = false
  question.value = {
    question: feedback.value.followUp,
    intent: '追问：核对你刚才回答里的细节与真实性',
    tips: ['先直接回答追问，再补一个具体细节', '不确定的地方说明边界，不硬编'],
    timeLimitSeconds: 90
  }
  questionNo.value += 1
  answer.value = ''
  feedback.value = null
  error.value = null
  startTimer()
  nextTick(() => {
    coachRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    answerRef.value?.focus()
  })
}

async function loadTargets() {
  try {
    const application = await api.myApplication()
    let list = (application?.choices || []).map((choice) => ({
      tutorId: choice.tutorId,
      tutorName: choice.tutorName,
      labName: choice.labName,
      researchDirection: choice.researchDirection
    }))
    if (!list.length) {
      const tutors = await api.studentTutors()
      list = (tutors || []).map((tutor) => ({
        tutorId: tutor.tutorId,
        tutorName: tutor.tutorName,
        labName: tutor.labName,
        researchDirection: tutor.researchDirection
      }))
    }
    tutorOptions.value = list
    if (!role.value.trim() && list[0]?.researchDirection) {
      role.value = list[0].researchDirection
    }
  } catch {
    /* 目标信息只影响出题语境，加载失败不阻塞练习 */
  }
}

async function loadHistory() {
  try {
    history.value = (await api.aiCoachHistory()) || []
  } catch {
    /* 历史记录失败不影响当前练习 */
  }
}

function openHistory(item) {
  historyDetail.value = item
  historyOpen.value = true
}

function offlineError(action) {
  return {
    title: '网络不可用',
    message: `当前处于离线状态，无法${action}。已填写的内容会保留在页面，恢复网络后可重试。`
  }
}

function toError(e, action) {
  if (navigator.onLine === false) return offlineError(action)
  if (e?.code === 429) {
    return { title: 'AI 额度不足，已暂停练习', message: e.message }
  }
  if (e?.timeout) {
    return {
      title: `${action}超时`,
      message: '服务端可能仍在处理，回答已保留在页面。请稍后重试；多次超时可把回答精简到 500 字以内。'
    }
  }
  if (e?.code === 400 || e?.code === 413) {
    return { title: '内容不符合要求', message: e.message }
  }
  if (/Network Error|Failed to fetch|网络/i.test(e?.message || '')) {
    return {
      title: '网络连接中断',
      message: '请求未完成，回答已保留在页面。请检查网络后重试。'
    }
  }
  return { title: `${action}失败`, message: e?.message || '未知错误，请稍后重试' }
}

onMounted(() => {
  loadTargets()
  loadHistory()
})

onBeforeUnmount(stopTimer)
</script>

<style scoped>
.head-gap {
  flex: 1;
}

/* ---------------- 环节选择 ---------------- */

.stage-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stage-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--surface);
  font-family: var(--font-ios);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s var(--ease-ios), background-color 0.2s var(--ease-ios),
    transform 0.18s var(--ease-ios), box-shadow 0.2s var(--ease-ios);
}

.stage-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.stage-card.active {
  border-color: rgba(88, 86, 214, 0.42);
  background: linear-gradient(135deg, rgba(88, 86, 214, 0.08), rgba(175, 82, 222, 0.04));
  box-shadow: 0 0 0 3px rgba(88, 86, 214, 0.08);
}

.stage-icon {
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #4b49b8;
  background: rgba(88, 86, 214, 0.12);
}

.stage-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.stage-title {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--label-1);
}

.stage-desc {
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--label-2);
}

.stage-meta {
  flex: none;
  font-size: 10.5px;
  color: var(--label-3);
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  background: var(--fill-2);
  white-space: nowrap;
}

/* ---------------- 布局 ---------------- */

.coach-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.coach-main {
  min-width: 0;
}

.coach-card {
  scroll-margin-top: 86px;
}

.coach-side {
  position: sticky;
  top: 86px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------------- 空状态 / 题目 ---------------- */

.coach-empty {
  margin-top: 16px;
  padding: 34px 20px;
  border-radius: 16px;
  background: var(--surface-2);
  border: 1.5px dashed var(--separator-strong);
  text-align: center;
}

.empty-icon {
  width: 54px;
  height: 54px;
  margin: 0 auto 10px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4b49b8;
  background: rgba(88, 86, 214, 0.1);
}

.empty-title {
  font-size: 15px;
  font-weight: 650;
  color: var(--label-1);
}

.coach-empty p {
  max-width: 48ch;
  margin: 6px auto 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--label-2);
}

.question-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(88, 86, 214, 0.1), rgba(48, 176, 199, 0.05));
}

.q-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #4b49b8;
}

.q-text {
  margin-top: 6px;
  font-size: 16px;
  line-height: 1.6;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--label-1);
}

.q-intent {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--label-2);
}

.q-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 10px;
}

.q-tips-label {
  font-size: 11px;
  color: var(--label-3);
  padding-top: 2px;
}

.q-tip {
  font-size: 11px;
  line-height: 1.5;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--label-2);
}

.q-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(88, 86, 214, 0.2);
}

/* ---------------- 作答 ---------------- */

.answer-block {
  margin-top: 14px;
}

.answer-block :deep(.el-textarea__inner) {
  font-family: var(--font-ios);
  font-size: 13.5px;
  line-height: 1.8;
  border-radius: 12px;
  background: var(--fill-2);
  box-shadow: none;
}

.answer-block :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(88, 86, 214, 0.28) inset;
  background: var(--surface);
}

.answer-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.answer-gap {
  flex: 1;
}

.over-text {
  color: var(--ios-red-dark);
  font-weight: 600;
}

.input-hint {
  margin: 7px 0 0;
  font-size: 11.5px;
  color: var(--ios-orange-dark);
}

/* ---------------- 侧栏 ---------------- */

.field {
  margin-top: 14px;
}

.field-label {
  margin-bottom: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--label-2);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.history-item {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: var(--fill-2);
  font-family: var(--font-ios);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s var(--ease-ios), border-color 0.2s var(--ease-ios);
}

.history-item:hover {
  background: var(--surface);
  border-color: rgba(88, 86, 214, 0.24);
}

.h-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.h-section {
  font-size: 11px;
  font-weight: 600;
  color: #4b49b8;
}

.h-score {
  font-size: 12px;
  font-weight: 700;
  color: #4b49b8;
  font-variant-numeric: tabular-nums;
}

.h-question {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--label-1);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.h-time {
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--label-3);
}

/* ---------------- 历史抽屉 ---------------- */

.history-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hd-meta {
  font-size: 12px;
  color: var(--label-3);
}

.hd-label {
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--label-3);
}

.hd-block p {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--label-1);
}

.hd-answer {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--fill-2);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1080px) {
  .coach-layout {
    grid-template-columns: 1fr;
  }

  .coach-side {
    position: static;
    order: -1;
  }
}

@media (max-width: 900px) {
  .stage-grid {
    grid-template-columns: 1fr;
  }

  .stage-card {
    align-items: center;
  }
}

@media (max-width: 620px) {
  .q-text {
    font-size: 15px;
  }

  .answer-foot {
    align-items: flex-start;
  }

  .answer-gap {
    display: none;
  }
}
</style>
