<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>导师与项目方向</h2>
        <p>宣讲与开放日阶段可先了解各实训室导师；进入「意向填报」阶段后可填报最多 3 个平行志愿。</p>
      </div>
      <el-button v-if="store.isStudent" type="primary" @click="router.push('/student/application')">
        去填报志愿
      </el-button>
    </div>

    <el-alert v-if="store.currentPhase" :closable="false" type="info" show-icon>
      <template #title>
        当前阶段：第 {{ store.currentRound }} 轮 · {{ store.currentPhase.name }}
        <span class="muted" style="margin-left: 8px">
          {{ fmtTime(store.currentPhase.startAt) }} ~ {{ fmtTime(store.currentPhase.endAt) }}
        </span>
      </template>
    </el-alert>

    <!-- AI 实训室推荐：技能匹配 + 名额 + 竞争度 -->
    <section v-if="store.isStudent" class="ai-card">
      <div class="ai-head">
        <div class="ai-mark">AI</div>
        <div>
          <div class="ai-title">AI 实训室推荐</div>
          <div class="ai-meta">按技能标签、名额余量与报考竞争度排序</div>
        </div>
        <span v-if="aiEngine" class="ai-badge">{{ aiEngine }}</span>
        <div class="spacer" />
        <el-button size="small" :loading="aiLoading" @click="runRecommend">
          {{ recommend ? '重新分析' : '开始分析' }}
        </el-button>
      </div>

      <p v-if="!recommend && !aiLoading" class="ai-desc">
        用你的技能标签匹配各实训室方向，给出「冲一冲 / 稳妥 / 保底」的志愿顺序建议。结果仅供参考，最终以双选流程为准。
      </p>

      <div v-if="aiLoading" class="ai-loading">
        <span class="ai-dots"><i /><i /><i /></span>
        正在匹配方向与竞争度…
      </div>

      <div v-if="aiError" class="ai-alert">
        <el-icon class="ai-alert-icon"><WarningFilled /></el-icon>
        <div>
          <div class="ai-alert-title">{{ aiError.quota ? 'AI 额度不足，已暂停分析' : 'AI 分析失败' }}</div>
          <div class="ai-alert-sub">{{ aiError.message }}</div>
        </div>
      </div>

      <template v-if="recommend">
        <div class="ai-list" style="margin-top: 12px">
          <div v-for="item in (recommend.items || []).slice(0, 5)" :key="item.tutorId" class="ai-item">
            <div class="rec-head">
              <span class="ai-rank">{{ item.suggestedRank }}</span>
              <div class="rec-main">
                <div class="ai-item-title">
                  {{ item.tutorName }}
                  <span class="muted">· {{ item.labName }}</span>
                </div>
                <div class="ai-item-sub">{{ item.researchDirection }}</div>
              </div>
              <div class="rec-score">
                <div class="ai-score">{{ item.matchScore }}</div>
                <div class="tiny">匹配度</div>
              </div>
            </div>
            <div v-if="(item.skillHits || []).length || (item.risks || []).length" class="ai-tags">
              <span v-for="skill in item.skillHits || []" :key="skill" class="ai-tag hit">{{ skill }}</span>
              <span v-for="risk in (item.risks || []).slice(0, 1)" :key="risk" class="ai-tag risk">{{ risk }}</span>
            </div>
            <div v-if="(item.reasons || []).length" class="ai-item-sub">
              {{ (item.reasons || []).slice(0, 2).join('；') }}
            </div>
          </div>
        </div>
        <p class="ai-note">{{ recommend.summary }}</p>
      </template>
    </section>

    <!-- 实训室概览：宣讲阶段学生最关心的横向对比信息 -->
    <section class="lab-overview">
      <div class="ov-head">
        <div>
          <h3>实训室概览</h3>
          <p class="muted">六个实训室的方向、技术栈与出口各不相同，先看清差异再决定志愿顺序。</p>
        </div>
        <span class="ov-stat">{{ labStat }}</span>
      </div>

      <div class="lab-cards">
        <article
          v-for="lab in labList"
          :key="lab.id"
          class="lab-card"
          :class="{ open: isLabOpen(lab.id) }"
        >
          <header class="lab-head" @click="toggleLab(lab.id)">
            <span class="lab-code">{{ lab.code }}</span>
            <div class="lab-main">
              <div class="lab-name">{{ lab.name }}</div>
              <div class="lab-sub">
                <template v-if="lab.leaderName">负责人 {{ lab.leaderName }}</template>
                <template v-else><span class="warn">负责人待确认</span></template>
                <span class="dot">·</span>{{ lab.tutorCount }} 位导师
                <span class="dot">·</span>{{ lab.totalQuota }} 个名额
              </div>
            </div>
            <el-icon class="chev"><ArrowDown /></el-icon>
          </header>

          <p class="lab-dir">{{ lab.description }}</p>

          <div v-show="isLabOpen(lab.id)" class="lab-detail">
            <div v-for="row in detailRows(lab)" :key="row.label" class="detail-row">
              <span class="detail-label">{{ row.label }}</span>
              <span class="detail-value">{{ row.value }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <div class="toolbar">
      <el-radio-group v-model="activeLab" size="default">
        <el-radio-button :value="0">全部实训室</el-radio-button>
        <el-radio-button v-for="lab in labs" :key="lab.labId" :value="lab.labId">{{ lab.labName }}</el-radio-button>
      </el-radio-group>
      <span class="muted">共 {{ filtered.length }} 位导师</span>
    </div>

    <div v-loading="loading" class="tutor-grid">
      <div v-for="tutor in filtered" :key="tutor.tutorId" class="tutor-card" :class="'tone-' + toneOf(tutor)">
        <div class="tutor-head">
          <div class="avatar">{{ tutor.tutorName.slice(0, 1) }}</div>
          <div>
            <div class="tutor-name">
              {{ tutor.tutorName }}
              <span class="title-tag">{{ tutor.title }}</span>
            </div>
            <div class="tutor-meta">{{ tutor.labName }}</div>
          </div>
        </div>

        <div class="chips">
          <span class="chip">{{ tutor.researchDirection }}</span>
          <span class="chip">报考 {{ tutor.applicantCount }} 人</span>
        </div>

        <div class="intro">{{ tutor.intro }}</div>

        <div class="quota-bar">
          <span>名额</span>
          <div class="quota-track">
            <div
              class="quota-fill"
              :class="'tone-' + usageTone(usedPercent(tutor))"
              :style="{ width: usedPercent(tutor) + '%' }"
            />
          </div>
          <span class="mono">{{ tutor.quota - tutor.remainingQuota }}/{{ tutor.quota }}</span>
        </div>

        <button v-if="store.isStudent" class="ask-avatar" type="button" @click="openAvatar(tutor)">
          <el-icon><ChatDotRound /></el-icon>
          <span>问问 {{ tutor.tutorName }} 老师的 AI 分身</span>
        </button>
      </div>
      <div v-if="!loading && !filtered.length" class="empty" style="grid-column: 1 / -1">暂无导师数据</div>
    </div>

    <AiAvatarDrawer v-model="avatarOpen" :tutor="avatarTutor" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { fmtTime } from '../../utils/labels.js'
import { labTone, usageTone } from '../../utils/tones.js'
import AiAvatarDrawer from '../../components/AiAvatarDrawer.vue'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const tutors = ref([])
const activeLab = ref(0)

// 实训室概览（实训室级对比信息，来自 /meta/labs）
const labList = ref([])
// 允许多个实训室同时展开；点击只切换被点的那个，不影响其他
const openLabIds = ref([])

function isLabOpen(id) {
  return openLabIds.value.includes(id)
}

const recommend = ref(null)
const aiLoading = ref(false)
const aiEngine = ref('')
const aiError = ref(null)
const avatarOpen = ref(false)
const avatarTutor = ref(null)

const labStat = computed(() => {
  if (!labList.value.length) return ''
  const tutors = labList.value.reduce((sum, l) => sum + (l.tutorCount || 0), 0)
  const quota = labList.value.reduce((sum, l) => sum + (l.totalQuota || 0), 0)
  return `${labList.value.length} 个实训室 · ${tutors} 位导师 · ${quota} 个名额`
})

const DETAIL_FIELDS = [
  ['techBackend', '后端/工程'],
  ['techFrontend', '前端/客户端'],
  ['aiDirection', 'AI/大模型'],
  ['outputTypes', '项目产出'],
  ['achievements', '竞赛/成果'],
  ['trainingMode', '培养模式'],
  ['graduationStandard', '结业标准'],
  ['suitableFor', '适合人群']
]

function detailRows(lab) {
  return DETAIL_FIELDS.filter(([key]) => lab[key]).map(([key, label]) => ({ label, value: lab[key] }))
}

function toggleLab(id) {
  const index = openLabIds.value.indexOf(id)
  if (index >= 0) {
    openLabIds.value.splice(index, 1)
  } else {
    openLabIds.value.push(id)
  }
}

const labs = computed(() => {
  const map = new Map()
  tutors.value.forEach((tutor) => {
    if (!map.has(tutor.labId)) {
      map.set(tutor.labId, { labId: tutor.labId, labName: tutor.labName })
    }
  })
  return [...map.values()]
})

const filtered = computed(() =>
  activeLab.value === 0 ? tutors.value : tutors.value.filter((t) => t.labId === activeLab.value)
)

function usedPercent(tutor) {
  if (!tutor.quota) return 0
  return Math.min(100, Math.round(((tutor.quota - tutor.remainingQuota) / tutor.quota) * 100))
}

function toneOf(tutor) {
  return labTone(tutor.labId, tutor.labName)
}

function engineLabel(res) {
  if (!res) return ''
  if (res.placeholder) return '规则引擎预览'
  return res.provider ? `${res.provider} · ${res.model}` : String(res.model || '模型')
}

async function runRecommend() {
  aiLoading.value = true
  aiError.value = null
  try {
    const res = await api.aiRecommendations()
    recommend.value = res?.data || null
    aiEngine.value = engineLabel(res)
  } catch (error) {
    recommend.value = null
    aiError.value = { message: error.message, quota: error.code === 429 }
  } finally {
    aiLoading.value = false
  }
}

function openAvatar(tutor) {
  avatarTutor.value = {
    ...tutor,
    displayName: `${tutor.tutorName}老师助手`,
    greeting: `你好，我是${tutor.tutorName}老师的 AI 分身，可以聊聊${tutor.researchDirection}方向、项目要求与面试准备。`
  }
  avatarOpen.value = true
}

onMounted(async () => {
  loading.value = true
  try {
    const [tutorData, labData] = await Promise.all([
      store.isStudent ? api.studentTutors() : api.allTutors(),
      api.labs()
    ])
    tutors.value = tutorData || []
    labList.value = labData || []
    // 首屏默认展开第一个实训室，其余保持收起
    if (labList.value.length) openLabIds.value = [labList.value[0].id]
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ---- 实训室概览 ---- */
.lab-overview {
  background: var(--surface);
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  padding: 18px 18px 14px;
}

.ov-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.ov-head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--label-1);
}

.ov-head p {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
}

.ov-stat {
  flex: none;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--label-2);
  background: var(--fill-2);
  border-radius: var(--radius-pill);
  padding: 5px 12px;
  white-space: nowrap;
}

.lab-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
  /* 关键：卡片按自身内容高度排列，展开一张不会把同排其他卡片一起拉长 */
  align-items: start;
}

.lab-card {
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--surface-2);
  padding: 12px 14px;
  transition: border-color 0.2s var(--ease-ios), background-color 0.2s var(--ease-ios);
}

.lab-card.open {
  border-color: rgba(88, 86, 214, 0.28);
  background: var(--surface);
}

.lab-head {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.lab-code {
  flex: none;
  width: 42px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xs);
  background: rgba(88, 86, 214, 0.1);
  color: #4b49b8;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.lab-main {
  flex: 1;
  min-width: 0;
}

.lab-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--label-1);
}

.lab-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--label-2);
}

.lab-sub .dot {
  margin: 0 5px;
  color: var(--label-3);
}

.lab-sub .warn {
  color: #b26a00;
}

.chev {
  flex: none;
  font-size: 13px;
  color: var(--label-3);
  transition: transform 0.24s var(--ease-ios);
}

.lab-card.open .chev {
  transform: rotate(180deg);
  color: #4b49b8;
}

.lab-dir {
  margin: 9px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--label-2);
  padding-left: 52px;
}

.lab-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--separator);
  display: grid;
  gap: 7px;
}

.detail-row {
  display: grid;
  grid-template-columns: 68px 1fr;
  gap: 10px;
  font-size: 12px;
  line-height: 1.65;
}

.detail-label {
  color: var(--label-3);
  white-space: nowrap;
}

.detail-value {
  color: var(--label-2);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.title-tag {
  font-size: 11px;
  font-weight: 400;
  color: var(--label-2);
  background: var(--fill-1);
  padding: 2px 7px;
  border-radius: 6px;
  margin-left: 6px;
}

.intro {
  font-size: 12.5px;
  color: var(--label-3);
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ask-avatar {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  margin-top: 2px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--surface);
  font-family: var(--font-ios);
  font-size: 12.5px;
  color: var(--label-2);
  cursor: pointer;
  transition: color 0.2s var(--ease-ios), background-color 0.2s var(--ease-ios),
    border-color 0.2s var(--ease-ios);
}

.ask-avatar:hover {
  color: #4b49b8;
  border-color: rgba(88, 86, 214, 0.35);
  background: rgba(88, 86, 214, 0.06);
}

.spacer {
  flex: 1;
}

.rec-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rec-main {
  flex: 1;
  min-width: 0;
}

.rec-score {
  flex: none;
  text-align: right;
  line-height: 1.2;
}

/* 窄窗口：概览卡片降为单列，避免每卡被压得过窄 */
@media (max-width: 700px) {
  .lab-cards {
    grid-template-columns: 1fr;
  }
}
</style>
