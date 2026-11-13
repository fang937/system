<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的志愿</h2>
        <p>最多填报 3 个平行志愿，不分先后；同时勾选是否服从实训室内调剂与全院调剂。</p>
      </div>
      <el-tag v-if="round" size="large" effect="plain">第 {{ round }} 轮</el-tag>
    </div>

    <el-alert v-if="!editable" :closable="false" :type="hasApplication ? 'success' : 'warning'" show-icon>
      <template #title>
        <template v-if="!applyWindow">
          当前没有进行中的填报阶段，无法提交志愿。
        </template>
        <template v-else-if="applyWindow.state === 'PENDING'">
          填报尚未开始：{{ fmtTime(applyWindow.startAt) }} 开放，{{ fmtTime(applyWindow.endAt) }} 截止。
        </template>
        <template v-else-if="applyWindow.state === 'CLOSED'">
          本阶段已结束（{{ fmtTime(applyWindow.endAt) }} 截止），志愿已锁定。
        </template>
      </template>
    </el-alert>

    <el-alert v-if="editable" :closable="false" type="success" show-icon>
      <template #title>
        填报进行中，截止 {{ fmtTime(applyWindow.endAt) }}，剩余 {{ countdown(applyWindow.endAt) }}
      </template>
    </el-alert>

    <!-- AI 面试技巧分析（预留真实模型接口，未配置 Key 时为规则引擎预览） -->
    <section class="ai-card ai-teal">
      <div class="ai-head">
        <div class="ai-mark">AI</div>
        <div>
          <div class="ai-title">AI 面试技巧分析</div>
          <div class="ai-meta">结合你的技能标签与志愿方向生成</div>
        </div>
        <span v-if="tipEngine" class="ai-badge">{{ tipEngine }}</span>
        <div class="spacer" />
        <el-button size="small" :loading="tipLoading" @click="runTips">
          {{ tips ? '重新分析' : '开始分析' }}
        </el-button>
      </div>

      <p v-if="!tips && !tipLoading" class="ai-desc">
        生成考察重点、大概率会问的问题、回答框架与自查清单。分析结果仅供参考，不构成录取承诺。
      </p>

      <div v-if="tipLoading" class="ai-loading">
        <span class="ai-dots"><i /><i /><i /></span>
        正在分析你的技能与目标方向…
      </div>

      <div v-if="tipError" class="ai-alert">
        <el-icon class="ai-alert-icon"><WarningFilled /></el-icon>
        <div>
          <div class="ai-alert-title">{{ tipError.quota ? 'AI 额度不足，已暂停分析' : 'AI 分析失败' }}</div>
          <div class="ai-alert-sub">{{ tipError.message }}</div>
        </div>
      </div>

      <template v-if="tips">
        <p class="ai-desc">{{ tips.summary }}</p>

        <div v-if="(tips.focus || []).length" class="ai-section-title">考察重点</div>
        <div class="ai-tags">
          <span v-for="item in tips.focus || []" :key="item.code" class="ai-tag">
            {{ item.name }} {{ item.weight }}%
          </span>
        </div>

        <template v-if="(tips.questions || []).length">
          <div class="ai-section-title">大概率会问</div>
          <div class="ai-list">
            <div v-for="(item, index) in tips.questions" :key="index" class="ai-item">
              <div class="ai-item-title">{{ item.question }}</div>
              <div v-if="item.intent" class="ai-item-sub">{{ item.intent }}</div>
              <div v-if="(item.outline || []).length" class="ai-item-sub">
                回答框架：{{ item.outline.join(' / ') }}
              </div>
            </div>
          </div>
        </template>

        <template v-if="(tips.tips || []).length">
          <div class="ai-section-title">准备建议</div>
          <div class="ai-list">
            <div v-for="(item, index) in tips.tips" :key="index" class="ai-item">
              <div class="ai-item-title">{{ item.title }}</div>
              <div v-if="item.detail" class="ai-item-sub">{{ item.detail }}</div>
            </div>
          </div>
        </template>

        <template v-if="(tips.selfCheck || []).length">
          <div class="ai-section-title">自查清单</div>
          <div class="ai-list">
            <div v-for="(item, index) in tips.selfCheck" :key="index" class="ai-item">{{ item }}</div>
          </div>
        </template>
      </template>
    </section>

    <el-card v-if="hasApplication" shadow="never" class="card">
      <div class="section-title">
        <h3>已提交的志愿</h3>
        <el-tag :type="statusOf(application.status).type" effect="light">
          {{ statusOf(application.status).label }}
        </el-tag>
      </div>
      <div class="choice-list">
        <div v-for="choice in choices" :key="choice.index" class="choice" :class="'tone-' + choiceTone(choice.index)">
          <span class="choice-index">{{ choice.index }}</span>
          <div class="choice-main">
            <div class="choice-name">{{ choice.tutorName }}<span class="muted"> · {{ choice.labName }}</span></div>
            <div class="tiny">{{ choice.title }} · {{ choice.researchDirection }}</div>
          </div>
          <span class="tiny">预计招收 {{ choice.quota }} 名 · 已报 {{ choice.applicantCount ?? 0 }} 人</span>
        </div>
      </div>
      <div class="flags">
        <el-tag :type="application.acceptLabAdjust ? 'success' : 'info'" effect="plain">
          {{ application.acceptLabAdjust ? '服从实训室内调剂' : '不服从实训室内调剂' }}
        </el-tag>
        <el-tag :type="application.acceptGlobalAdjust ? 'success' : 'info'" effect="plain">
          {{ application.acceptGlobalAdjust ? '服从全院调剂' : '不服从全院调剂' }}
        </el-tag>
        <span class="tiny">提交时间：{{ fmtTime(application.submittedAt) }}</span>
      </div>
    </el-card>

    <el-card v-if="editable" shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>{{ hasApplication ? '修改志愿' : '选择志愿' }}</h3>
        <span class="muted">已选 {{ selected.length }}/3</span>
      </div>

      <div class="lab-filter">
        <el-radio-group v-model="activeLab" size="small">
          <el-radio-button :value="0">全部</el-radio-button>
          <el-radio-button v-for="lab in labs" :key="lab.labId" :value="lab.labId">{{ lab.labName }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="tutor-grid">
        <div
          v-for="tutor in pickable"
          :key="tutor.tutorId"
          class="tutor-card pick"
          :class="['tone-' + toneOf(tutor), { selected: selected.includes(tutor.tutorId) }]"
          @click="toggle(tutor.tutorId)"
        >
          <div class="tutor-head">
            <div class="avatar">{{ tutor.tutorName.slice(0, 1) }}</div>
            <div>
              <div class="tutor-name">{{ tutor.tutorName }}<span class="title-tag">{{ tutor.title }}</span></div>
              <div class="tutor-meta">{{ tutor.labName }}</div>
            </div>
            <span v-if="selected.includes(tutor.tutorId)" class="order-badge">
              志愿 {{ selected.indexOf(tutor.tutorId) + 1 }}
            </span>
          </div>
          <div class="chips">
            <span class="chip">{{ tutor.researchDirection }}</span>
          </div>
          <div class="quota-bar">
            <span class="quota-text">预计招收 {{ tutor.quota }} 名</span>
            <div class="quota-track">
              <div class="quota-fill" :style="{ width: applyPercent(tutor) + '%' }" />
            </div>
            <span class="mono">已报 {{ tutor.applicantCount }} 人</span>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="switch-row">
        <el-switch v-model="acceptLab" active-text="服从实训室内调剂" />
        <el-switch v-model="acceptGlobal" active-text="服从全院调剂" />
      </div>

      <div class="submit-row">
        <el-button @click="reset">清空选择</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ hasApplication ? '保存修改' : '提交志愿' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { applicationStatusMap, countdown, fmtTime } from '../../utils/labels.js'
import { labTone } from '../../utils/tones.js'

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const round = ref(1)
const applyWindow = ref(null)
const application = ref(null)
const choices = ref([])
const tutors = ref([])
const selected = ref([])
const acceptLab = ref(true)
const acceptGlobal = ref(false)
const activeLab = ref(0)
const tips = ref(null)
const tipLoading = ref(false)
const tipEngine = ref('')
const tipError = ref(null)

const hasApplication = computed(() => Boolean(application.value))
const editable = computed(() => applyWindow.value?.state === 'ACTIVE' && application.value?.status !== 'MATCHED')

const labs = computed(() => {
  const map = new Map()
  tutors.value.forEach((t) => {
    if (!map.has(t.labId)) map.set(t.labId, { labId: t.labId, labName: t.labName })
  })
  return [...map.values()]
})

const pickable = computed(() =>
  activeLab.value === 0 ? tutors.value : tutors.value.filter((t) => t.labId === activeLab.value)
)

function statusOf(value) {
  return applicationStatusMap[value] || { label: value || '—', type: 'info' }
}

function applyPercent(tutor) {
  // 进度条表达「已报人数 / 预计招收名额」的竞争度，100% 封顶
  if (!tutor.quota) return 0
  return Math.min(100, Math.round(((tutor.applicantCount || 0) / tutor.quota) * 100))
}

function toneOf(tutor) {
  return labTone(tutor.labId, tutor.labName)
}

function choiceTone(index) {
  return ['blue', 'indigo', 'teal'][index - 1] || 'blue'
}

function toggle(tutorId) {
  const index = selected.value.indexOf(tutorId)
  if (index >= 0) {
    selected.value.splice(index, 1)
    return
  }
  if (selected.value.length >= 3) {
    ElMessage.warning('最多只能填报 3 个平行志愿')
    return
  }
  selected.value.push(tutorId)
}

function reset() {
  selected.value = []
}

async function load() {
  loading.value = true
  try {
    const [app, list] = await Promise.all([api.myApplication(), api.studentTutors()])
    round.value = app.round
    applyWindow.value = app.applyWindow
    application.value = app.application
    choices.value = app.choices || []
    tutors.value = list
    if (app.application) {
      selected.value = [app.application.tutor1Id, app.application.tutor2Id, app.application.tutor3Id].filter(Boolean)
      acceptLab.value = app.application.acceptLabAdjust === 1
      acceptGlobal.value = app.application.acceptGlobalAdjust === 1
    } else {
      acceptLab.value = true
      acceptGlobal.value = false
    }
  } finally {
    loading.value = false
  }
}

async function runTips() {
  tipLoading.value = true
  tipError.value = null
  try {
    const res = await api.aiInterviewTips()
    tips.value = res?.data || null
    if (res?.placeholder) {
      tipEngine.value = '规则引擎预览'
    } else {
      tipEngine.value = res?.provider ? `${res.provider} · ${res.model}` : String(res?.model || '模型')
    }
  } catch (error) {
    tips.value = null
    tipError.value = { message: error.message, quota: error.code === 429 }
  } finally {
    tipLoading.value = false
  }
}

async function submit() {  if (!selected.value.length) {
    ElMessage.warning('请至少选择 1 个志愿')
    return
  }
  saving.value = true
  try {
    await api.submitApplication({
      tutorIds: selected.value,
      acceptLabAdjust: acceptLab.value,
      acceptGlobalAdjust: acceptGlobal.value
    })
    ElMessage.success('志愿已提交')
    await load()
    await store.refreshPhase()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.choice-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.choice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--separator);
  border-left: 3px solid var(--tone, var(--ios-blue));
  border-radius: 12px;
  background: var(--surface-2);
}

.choice-index {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: var(--tone-soft, var(--el-color-primary-light-9));
  color: var(--tone-deep, var(--el-color-primary));
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.choice-main {
  flex: 1;
  min-width: 0;
}

.choice-name {
  font-size: 14px;
  font-weight: 500;
}

.flags {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.lab-filter {
  margin-bottom: 14px;
}

.pick {
  cursor: pointer;
}

/* 名额行：左侧预计招收名额、中间竞争度进度条、右侧已报人数，三者不换行 */
.tutor-card .quota-bar {
  gap: 6px;
}

.quota-text {
  white-space: nowrap;
}

.quota-bar .mono {
  white-space: nowrap;
}

.order-badge {
  margin-left: auto;
  font-size: 11px;
  background: var(--tone, var(--el-color-primary));
  color: #fff;
  padding: 2px 8px;
  border-radius: 999px;
}

.switch-row {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.spacer {
  flex: 1;
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
</style>
