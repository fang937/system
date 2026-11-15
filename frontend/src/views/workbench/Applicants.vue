<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>报考我的学生</h2>
        <p>按面试成绩排序；名额锁定后不可超发，撤销拟录取会自动释放名额并顺延递补。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 流程横幅：四个里程碑，状态取真实 phase，不是装饰 -->
    <section class="hero">
      <span class="hero-glow hero-glow-a" aria-hidden="true" />
      <span class="hero-glow hero-glow-b" aria-hidden="true" />

      <header class="hero-head">
        <div>
          <div class="hero-eyebrow">
            第 {{ round }} 轮 · {{ store.currentPhase?.name || '等待开启' }}
          </div>
          <h3 class="hero-title">我的招生进度</h3>
          <p class="hero-sub">
            <template v-if="store.currentPhase">
              {{ fmtTime(store.currentPhase.startAt) }} — {{ fmtTime(store.currentPhase.endAt) }}
            </template>
            <template v-else>当前没有进行中的阶段，名单仍可查看</template>
          </p>
        </div>
        <span class="hero-state" :class="store.currentPhase?.state === 'ACTIVE' ? 'on' : 'off'">
          <i />
          {{ store.currentPhase?.state === 'ACTIVE' ? '进行中' : '未开放' }}
        </span>
      </header>

      <ol class="hero-track">
        <li v-for="(item, index) in milestones" :key="item.code" class="hero-node" :class="item.state.toLowerCase()">
          <span class="hero-dot">
            <el-icon v-if="item.state === 'CLOSED'"><Select /></el-icon>
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="hero-label">{{ item.label }}</span>
          <span class="hero-desc">{{ item.hint }}</span>
        </li>
      </ol>
    </section>

    <div class="stat-grid">
      <div class="stat tone-blue">
        <div class="stat-top">
          <span class="stat-icon"><el-icon><Tickets /></el-icon></span>
          <span class="label">总名额</span>
        </div>
        <div class="value mono">{{ overview.quota ?? '—' }}</div>
        <div class="hint">当前账号导师名额</div>
      </div>
      <div class="stat tone-green">
        <div class="stat-top">
          <span class="stat-icon"><el-icon><Lock /></el-icon></span>
          <span class="label">剩余名额</span>
        </div>
        <div class="value mono">{{ overview.remainingQuota ?? '—' }}</div>
        <div class="hint">拟录取将立即占用</div>
      </div>
      <div class="stat tone-indigo">
        <div class="stat-top">
          <span class="stat-icon"><el-icon><UserFilled /></el-icon></span>
          <span class="label">报考人数</span>
        </div>
        <div class="value mono">{{ overview.applicantCount ?? 0 }}</div>
        <div class="hint">含 3 个平行志愿命中本人</div>
      </div>
      <div class="stat tone-orange">
        <div class="stat-top">
          <span class="stat-icon"><el-icon><Clock /></el-icon></span>
          <span class="label">待处理</span>
        </div>
        <div class="value mono">{{ overview.pendingCount ?? 0 }}</div>
        <div class="hint">尚未打分或处置</div>
      </div>
    </div>

    <el-alert v-if="!interviewOpen" :closable="false" type="info" show-icon>
      <template #title>
        当前不在「面试与初选」阶段，打分与处置已锁定，仅可查看名单。
      </template>
    </el-alert>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>学生名单</h3>
        <div class="head-tools">
          <el-radio-group v-model="round" size="small" @change="load">
            <el-radio-button :value="1">第 1 轮</el-radio-button>
            <el-radio-button :value="2">第 2 轮</el-radio-button>
            <el-radio-button :value="3">第 3 轮</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="学生" min-width="150">
          <template #default="{ row }">
            <div class="cell-name">{{ row.studentName }}</div>
            <div class="tiny">
              {{ row.studentNo }}
              <span v-if="row.resumeName" class="resume-flag">· 简历已上传</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="专业 / 班级" min-width="170">
          <template #default="{ row }">
            <div>{{ row.major || '—' }}</div>
            <div class="tiny">{{ row.className || '—' }}<span v-if="row.gpa"> · GPA {{ row.gpa }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="技能标签" min-width="150">
          <template #default="{ row }">
            <div class="chips">
              <span v-for="tag in splitTags(row.skillTags)" :key="tag" class="chip">{{ tag }}</span>
              <span v-if="!row.skillTags" class="tiny">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="志愿" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">第 {{ row.choiceIndex }} 志愿</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面试成绩" width="110" align="center">
          <template #default="{ row }">
            <span :class="{ 'score-strong': row.score != null }">{{ row.score ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处置" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.offerStatus).type" effect="light" size="small">
              {{ statusOf(row.offerStatus).label }}
            </el-tag>
            <div v-if="row.offerStatus === 1" class="tiny">{{ confirmLabel(row.confirmStatus) }}</div>
            <div v-if="row.offerStatus === 2 && row.reserveRank" class="tiny">顺位 {{ row.reserveRank }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="!interviewOpen" @click="openDialog(row)">
              打分 / 处置
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">本轮暂无学生报考你</div>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="面试评分与处置" width="640px">
      <div v-if="current" class="dialog-student">
        <div class="avatar">{{ current.studentName.slice(0, 1) }}</div>
        <div>
          <div class="cell-name">{{ current.studentName }}</div>
          <div class="tiny">{{ current.studentNo }} · {{ current.major || '—' }} · 第 {{ current.choiceIndex }} 志愿</div>
        </div>
        <div class="spacer" />
        <el-button v-if="current?.resumeName" size="small" text type="primary" @click="openResume(current)">
          查看简历
        </el-button>
        <el-tag size="small" effect="plain">剩余名额 {{ overview.remainingQuota ?? 0 }}</el-tag>
      </div>

      <section class="ai-review">
        <div class="ai-head">
          <div class="ai-mark">AI</div>
          <div>
            <div class="ai-title">AI 简历初评</div>
            <div class="ai-meta">基于档案与志愿的初评，供提问与打分参考</div>
          </div>
          <span v-if="reviewEngine" class="ai-badge">{{ reviewEngine }}</span>
          <div class="spacer" />
          <el-button size="small" :loading="reviewLoading" @click="runReview">生成初评</el-button>
        </div>

        <div v-if="reviewLoading" class="ai-loading">
          <span class="ai-dots"><i /><i /><i /></span>
          <span>正在阅读档案与志愿… 已等待 {{ reviewElapsed }}s</span>
          <span v-if="reviewElapsed >= 6" class="ai-loading-hint">
            首次评扫描件简历要先解析正文，耗时较长；解析过的简历会缓存，下次很快
          </span>
        </div>

        <div v-if="reviewError" class="ai-alert">
          <el-icon class="ai-alert-icon"><WarningFilled /></el-icon>
          <div>
            <div class="ai-alert-title">
              {{ reviewError.quota ? 'AI 额度不足，暂时无法初评' : reviewError.timeout ? '等待超时' : 'AI 初评失败' }}
            </div>
            <div class="ai-alert-sub">
              {{ reviewError.message }}
              <template v-if="reviewError.timeout">。简历解析在后台已可能完成，直接点「生成初评」重试即可。</template>
            </div>
          </div>
        </div>

        <template v-if="review">
          <p class="ai-desc">{{ review.summary?.headline }}</p>
          <div class="ai-tags">
            <span class="ai-tag hit">建议总分 {{ review.summary?.suggestedTotal }}</span>
            <span class="ai-tag">{{ review.summary?.recommendation }}</span>
            <span v-if="review.resumeDigest" class="ai-tag" :class="{ hit: review.resumeDigest.used }">
              {{ digestLabel(review.resumeDigest) }}
            </span>
          </div>

          <template v-if="(review.resumeTags || []).length">
            <div class="ai-section-title">简历技能标签（{{ review.resumeTags.length }} 项）</div>
            <div class="skill-cloud">
              <span v-for="tag in review.resumeTags" :key="tag.name" class="skill-pill" :title="tag.category">
                {{ tag.name }}
              </span>
            </div>
          </template>

          <template v-if="(review.resumeProfile?.experiences || []).length">
            <div class="ai-section-title">项目与实践经历</div>
            <ul class="exp-list">
              <li v-for="(item, index) in review.resumeProfile.experiences" :key="index">{{ item }}</li>
            </ul>
          </template>

          <template v-if="(review.resumeProfile?.traits || []).length">
            <div class="ai-section-title">性格与特质</div>
            <div class="skill-cloud">
              <span v-for="trait in review.resumeProfile.traits" :key="trait" class="trait-pill">{{ trait }}</span>
            </div>
          </template>

          <template v-if="(review.highlights || []).length">
            <div class="ai-section-title">亮点</div>
            <div class="ai-list">
              <div v-for="(item, index) in review.highlights" :key="index" class="ai-item">{{ item }}</div>
            </div>
          </template>

          <template v-if="(review.risks || []).length">
            <div class="ai-section-title">需要核实</div>
            <div class="ai-list">
              <div v-for="(item, index) in review.risks" :key="index" class="ai-item">{{ item }}</div>
            </div>
          </template>

          <template v-if="(review.suggestedQuestions || []).length">
            <div class="ai-section-title">建议提问</div>
            <div class="ai-list">
              <div v-for="(item, index) in review.suggestedQuestions" :key="index" class="ai-item">
                <div class="ai-item-title">{{ item.question }}</div>
                <div v-if="item.intent" class="ai-item-sub">{{ item.intent }}</div>
              </div>
            </div>
          </template>

          <div class="ai-actions">
            <el-button size="small" type="primary" plain @click="applyReview">采用建议分</el-button>
            <span class="tiny">仅填入评分滑杆，仍需你确认后保存</span>
          </div>
          <p v-if="review.disclaimer" class="ai-note">{{ review.disclaimer }}</p>
        </template>
      </section>

      <el-form label-position="top" class="dialog-form">
        <el-form-item label="面试成绩（0-100）">
          <el-input-number v-model="form.score" :min="0" :max="100" :precision="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="多维评分（按评分标准加权折算总分）">
          <div class="rubric-box">
            <div v-for="item in rubric" :key="item.code" class="rubric-row">
              <div class="rubric-name">
                {{ item.name }}
                <span>权重 {{ item.weight }}%</span>
              </div>
              <el-slider
                v-model="dims[item.code]"
                :min="0"
                :max="100"
                :step="1"
                :show-tooltip="false"
                size="small"
              />
              <div class="rubric-value">{{ dims[item.code] ?? '—' }}</div>
            </div>
            <div class="rubric-total">
              <span class="muted">加权总分</span>
              <strong class="mono">{{ weightedTotal ?? '—' }}</strong>
              <el-button size="small" text type="primary" :disabled="weightedTotal == null" @click="form.score = weightedTotal">
                填入总分
              </el-button>
            </div>
            <div class="tiny">标准：{{ rubricSummary }}</div>
          </div>
        </el-form-item>
        <el-form-item label="成绩构成 / 备注">
          <el-input v-model="form.scoreDetail" type="textarea" :rows="2" placeholder="如：专业基础 40 + 项目经历 35 + 表达 20" />
        </el-form-item>
        <el-form-item label="处置结果">
          <el-radio-group v-model="form.offerStatus">
            <el-radio-button :value="1">拟录取</el-radio-button>
            <el-radio-button :value="2">递补</el-radio-button>
            <el-radio-button :value="3">淘汰</el-radio-button>
            <el-radio-button :value="0">撤销</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <div class="tiny dialog-tip">
          拟录取会立即占用 1 个名额并给该学生发放 Offer；撤销/淘汰会释放名额，系统自动把顺位最靠前的递补转为拟录取。
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Lock, Refresh, Select, Tickets, UserFilled } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { fmtTime, offerStatusMap } from '../../utils/labels.js'
import { saveBlob } from '../../utils/download.js'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const overview = ref({})
const round = ref(store.currentRound || 1)
const dialogVisible = ref(false)
const current = ref(null)
const form = reactive({ score: null, scoreDetail: '', offerStatus: 0 })

/* AI 初评与多维评分 */
const rubric = ref([])
const dims = reactive({})
const review = ref(null)
const reviewLoading = ref(false)
const reviewEngine = ref('')
const reviewError = ref(null)

/** 初评是长任务（首次含 OCR），把已等待时长显示出来，避免用户以为卡死 */
const reviewElapsed = ref(0)
let reviewTimer = null

function startReviewClock() {
  reviewElapsed.value = 0
  const startedAt = Date.now()
  clearInterval(reviewTimer)
  reviewTimer = setInterval(() => {
    reviewElapsed.value = Math.floor((Date.now() - startedAt) / 1000)
  }, 500)
}

function stopReviewClock() {
  clearInterval(reviewTimer)
  reviewTimer = null
}

const rubricSummary = computed(() =>
  rubric.value.map((item) => `${item.name} ${item.weight}%`).join(' · ')
)

const weightedTotal = computed(() => {
  const rows = rubric.value.filter((item) => dims[item.code] !== null && dims[item.code] !== undefined)
  if (!rows.length) return null
  const weightSum = rows.reduce((sum, item) => sum + item.weight, 0)
  if (!weightSum) return null
  const total = rows.reduce((sum, item) => sum + dims[item.code] * item.weight, 0) / weightSum
  return Math.round(total * 10) / 10
})

function resetDims() {
  Object.keys(dims).forEach((key) => delete dims[key])
}

const interviewOpen = computed(() => store.currentPhase?.code === 'INTERVIEW' && store.currentPhase?.state === 'ACTIVE')

/** 横幅上的四个里程碑：状态直接取后端 phase，与列表、按钮的放行口径一致 */
const MILESTONES = [
  { code: 'APPLY', label: '意向填报', hint: '学生提交 3 个平行志愿' },
  { code: 'INTERVIEW', label: '面试考核', hint: '打分、拟录取与递补' },
  { code: 'CONFIRM', label: '拟录取确认', hint: '学生确认唯一去向' },
  { code: 'PUBLISH', label: '结果公示', hint: '公示后正式入组' }
]

const milestones = computed(() =>
  MILESTONES.map((item) => {
    const list = (store.phase?.phases || []).filter((p) => p.code === item.code)
    const hit =
      list.find((p) => p.roundNo === null || p.roundNo === undefined) ||
      list.find((p) => Number(p.roundNo) === Number(round.value)) ||
      list[0] ||
      null
    return { ...item, state: hit?.state || 'PENDING' }
  })
)

function statusOf(value) {
  return offerStatusMap[value ?? 0] || offerStatusMap[0]
}

function confirmLabel(value) {
  return ['待确认', '已确认', '超时放弃', '已放弃'][value ?? 0] || '—'
}

function splitTags(text) {
  return text ? String(text).split(/[,，;；]/).filter(Boolean) : []
}

async function load() {
  loading.value = true
  try {
    const [list, ov] = await Promise.all([api.applicants(round.value), api.workbenchOverview()])
    rows.value = list
    overview.value = ov
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  current.value = row
  form.score = row.score ?? null
  form.scoreDetail = row.scoreDetail || ''
  form.offerStatus = row.offerStatus ?? 0
  review.value = null
  reviewEngine.value = ''
  reviewError.value = null
  resetDims()
  dialogVisible.value = true
}

async function openResume(row) {
  try {
    const blob = await api.applicantResumeBlob(row.applicationId)
    saveBlob(blob, row.resumeName || '简历.pdf')
  } catch (error) {
    ElMessage.error(error.message || '简历下载失败')
  }
}

async function loadRubric() {  if (rubric.value.length) return
  try {
    rubric.value = await api.aiRubric()
  } catch {
    rubric.value = []
  }
}

async function runReview() {
  if (!current.value) return
  reviewLoading.value = true
  reviewError.value = null
  startReviewClock()
  try {
    const res = await api.aiResumeReview(current.value.applicationId)
    review.value = res?.data || null
    if (res?.placeholder) {
      reviewEngine.value = '规则引擎预览'
    } else {
      reviewEngine.value = res?.provider ? `${res.provider} · ${res.model}` : String(res?.model || '模型')
    }
  } catch (error) {
    review.value = null
    reviewError.value = {
      message: error.message,
      quota: error.code === 429,
      timeout: Boolean(error.timeout)
    }
  } finally {
    stopReviewClock()
    reviewLoading.value = false
  }
}

function digestLabel(digest) {
  if (!digest) return ''
  if (!digest.used) {
    const map = {
      none: '未上传简历',
      missing: '简历文件缺失',
      unsupported: '该格式暂不支持解析',
      encrypted: 'PDF 已加密，无法解析',
      failed: '简历解析失败'
    }
    return map[digest.engine] || '未解析到简历正文'
  }
  const engine = {
    'pdf-text': 'PDF 文本层',
    'pp-structure-v3': 'PP-StructureV3 文档解析',
    paddleocr: 'PaddleOCR',
    'docx-text': 'Word 正文'
  }[digest.engine] || digest.engine
  return `已读简历正文 ${digest.chars} 字 · ${engine}`
}

function applyReview() {  const dimensions = review.value?.dimensions || []
  dimensions.forEach((item) => {
    if (item.code && item.score != null) {
      dims[item.code] = Math.max(0, Math.min(100, Math.round(Number(item.score))))
    }
  })
  if (review.value?.summary?.suggestedTotal != null) {
    form.score = Number(review.value.summary.suggestedTotal)
  }
}

async function save() {
  if (form.score === null || form.score === undefined) {
    ElMessage.warning('请填写面试成绩')
    return
  }
  if (form.offerStatus === 1 && (overview.value.remainingQuota ?? 0) <= 0 && current.value.offerStatus !== 1) {
    ElMessage.warning('名额已用完，无法标记拟录取')
    return
  }
  if (form.offerStatus === 3) {
    try {
      await ElMessageBox.confirm(`确认淘汰 ${current.value.studentName}？`, '淘汰确认', {
        confirmButtonText: '确认淘汰',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  saving.value = true
  try {
    const items = rubric.value
      .filter((item) => dims[item.code] !== null && dims[item.code] !== undefined)
      .map((item) => ({ code: item.code, score: dims[item.code], comment: '' }))

    if (items.length) {
      // 有维度分时按评分标准折算总分，并保留明细
      await api.saveScores(current.value.applicationId, items)
    } else {
      await api.saveScore(current.value.applicationId, {
        score: form.score,
        scoreDetail: form.scoreDetail,
        remark: form.scoreDetail
      })
    }
    await api.decide(current.value.applicationId, {
      offerStatus: form.offerStatus,
      remark: form.scoreDetail
    })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadRubric()
})

onUnmounted(stopReviewClock)
</script>

<style scoped>
/* ============================================================================
   流程横幅
   ============================================================================ */

.hero {
  position: relative;
  overflow: hidden;
  padding: 24px 28px 26px;
  border-radius: 28px;
  /* 比页面底色更实一档，才能在薄荷背景上立起来 */
  background:
    radial-gradient(120% 110% at 84% -30%, rgba(255, 255, 255, 0.96), transparent 54%),
    linear-gradient(130deg, #dff7ef 0%, #c6ece1 46%, #f7ecd7 100%);
  border: 1px solid rgba(255, 255, 255, 0.96);
  box-shadow:
    0 26px 50px -34px rgba(20, 78, 70, 0.95),
    0 2px 6px rgba(20, 78, 70, 0.04),
    inset 0 1px 0 #fff;
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(38px);
}

.hero-glow-a {
  width: 260px;
  height: 260px;
  right: -70px;
  top: -110px;
  background: rgba(255, 255, 255, 0.85);
}

.hero-glow-b {
  width: 220px;
  height: 220px;
  left: -60px;
  bottom: -120px;
  background: rgba(240, 190, 130, 0.28);
}

.hero-head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.hero-eyebrow {
  display: inline-block;
  padding: 3px 11px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--brand-700);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: inset 0 0 0 1px rgba(47, 157, 146, 0.14);
}

.hero-title {
  margin: 10px 0 4px;
  font-size: 22px;
  font-weight: 780;
  letter-spacing: -0.03em;
  color: var(--ink-900);
}

.hero-sub {
  margin: 0;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--ink-600);
}

.hero-state {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  background: rgba(91, 125, 119, 0.1);
  color: var(--ink-500);
}

.hero-state i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.hero-state.on {
  background: var(--brand-500);
  color: #fff;
  box-shadow: 0 10px 20px -12px rgba(47, 157, 146, 1);
}

.hero-state.on i {
  background: #fff;
  animation: hero-pulse 1.9s ease-in-out infinite;
}

@keyframes hero-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.6);
  }
  60% {
    box-shadow: 0 0 0 5px rgba(255, 255, 255, 0);
  }
}

/* ---- 里程碑轨道 ---- */

.hero-track {
  position: relative;
  z-index: 1;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 24px 0 0;
  padding: 0;
}

.hero-track::before {
  content: '';
  position: absolute;
  top: 23px;
  left: 12.5%;
  right: 12.5%;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, rgba(47, 157, 146, 0.4), rgba(217, 164, 95, 0.42));
}

.hero-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  text-align: center;
}

.hero-dot {
  position: relative;
  z-index: 1;
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--ink-400);
  background: #fff;
  border: 2px solid rgba(28, 111, 102, 0.1);
  box-shadow: 0 8px 18px -14px rgba(20, 78, 70, 0.9), inset 0 1px 0 #fff;
  transition: transform 0.3s var(--clay-ease);
}

.hero-node.active .hero-dot {
  color: #fff;
  border-color: transparent;
  background: linear-gradient(150deg, #4fc0b2, #2f9d92 62%, #248a80);
  box-shadow: 0 0 0 5px rgba(47, 157, 146, 0.14), 0 14px 24px -16px rgba(47, 157, 146, 1);
  transform: scale(1.06);
}

.hero-node.closed .hero-dot {
  color: var(--brand-600);
  background: #ddf4ec;
  border-color: rgba(255, 255, 255, 0.9);
}

.hero-node.pending .hero-dot {
  border-style: dashed;
  border-color: rgba(217, 164, 95, 0.45);
  color: var(--sand-600);
}

.hero-label {
  padding: 4px 13px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--ink-800);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}

.hero-node.active .hero-label {
  color: #fff;
  background: var(--brand-500);
  box-shadow: 0 8px 16px -12px rgba(47, 157, 146, 1);
}

.hero-node.pending .hero-label {
  color: var(--sand-600);
  background: rgba(255, 255, 255, 0.62);
}

.hero-desc {
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ink-500);
  max-width: 20ch;
  text-wrap: balance;
}

/* ============================================================================
   统计卡
   ============================================================================ */

.stat-top {
  display: flex;
  align-items: center;
  gap: 9px;
}

.stat-icon {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 10px;
  font-size: 15px;
  background: rgba(47, 157, 146, 0.1);
  color: var(--brand-700);
}

.stat.tone-green .stat-icon {
  background: rgba(63, 156, 116, 0.12);
  color: #2f7d5c;
}

.stat.tone-indigo .stat-icon {
  background: rgba(90, 143, 191, 0.13);
  color: #3f6c96;
}

.stat.tone-orange .stat-icon {
  background: rgba(217, 164, 95, 0.16);
  color: var(--sand-600);
}

.stat .value {
  margin-top: 12px;
}

@media (max-width: 900px) {
  .hero {
    padding: 20px 18px 22px;
    border-radius: 22px;
  }

  .hero-title {
    font-size: 19px;
  }

  .hero-track {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px 6px;
  }

  .hero-track::before {
    display: none;
  }

  .hero-dot {
    width: 40px;
    height: 40px;
    font-size: 13px;
  }
}

/* 简历技能标签 */
.skill-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.skill-pill {
  font-size: 11.5px;
  line-height: 1.5;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(0, 122, 255, 0.1);
  color: var(--ios-blue);
  white-space: nowrap;
}

.trait-pill {
  font-size: 11.5px;
  line-height: 1.5;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(52, 199, 89, 0.12);
  color: var(--ios-green-dark);
  white-space: nowrap;
}

.exp-list {
  margin: 6px 0 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.exp-list li {
  font-size: 12px;
  line-height: 1.7;
  color: var(--label-1);
}

.head-tools {
  display: flex;
  gap: 10px;
}

.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}

.resume-flag {
  color: var(--ios-green-dark);
}

.score-strong {
  font-weight: 600;
  color: var(--el-color-primary);
}

.dialog-student {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--line);
  margin-bottom: 16px;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.dialog-tip {
  line-height: 1.7;
  background: var(--fill-2);
  border-radius: 8px;
  padding: 10px 12px;
}

.ai-review {
  padding: 14px;
  margin-bottom: 16px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(88, 86, 214, 0.06), rgba(88, 86, 214, 0) 120px),
    var(--fill-2);
}

.ai-review .ai-head {
  gap: 9px;
}

/* 长任务提示：等待超过 6s 才出现，避免正常请求也刷提示 */
.ai-loading {
  flex-wrap: wrap;
  row-gap: 4px;
}

.ai-loading-hint {
  flex-basis: 100%;
  padding-left: 2px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--sand-600);
}

.rubric-box {
  width: 100%;
  padding: 4px 2px 2px;
}

.rubric-total {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--separator);
  font-size: 12.5px;
}

.rubric-total strong {
  font-size: 17px;
  color: #4b49b8;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.rubric-total .el-button {
  margin-left: auto;
}

.spacer {
  flex: 1;
}
</style>
