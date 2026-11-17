<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>阶段与轮次</h2>
        <p>
          时间窗口是后端唯一校验依据：前端按钮只是提示，所有写接口都会按这里的配置做绝对时间校验。
          三轮录取按轮次折叠，展开后管理该轮的面试与确认；修改录取时间会带动这两段同步缩放。
        </p>
      </div>
      <div class="head-tools">
        <el-select v-model="round" size="default" style="width: 140px" @change="changeRound">
          <el-option :value="1" label="当前：第 1 轮" />
          <el-option :value="2" label="当前：第 2 轮" />
          <el-option :value="3" label="当前：第 3 轮" />
        </el-select>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="danger" plain :loading="resetting" @click="resetAll">重置阶段</el-button>
      </div>
    </div>

    <SelectionTimeline :phases="phases" :round="round" can-edit class="timeline-block" />

    <!-- 前置阶段 -->
    <el-card shadow="never" class="card card-pad section-card">
      <div class="section-title">
        <h3>前置阶段</h3>
        <span class="muted">宣讲与开放日，只读阶段</span>
      </div>
      <div class="phase-list">
        <div v-for="phase in leadPhases" :key="phase.id" class="phase-row" :class="phase.state.toLowerCase()">
          <div class="phase-seq">{{ phase.seq }}</div>
          <div class="phase-main">
            <div class="phase-name">
              {{ phase.name }}
              <el-tag size="small" :type="stateOf(phase.state).type" effect="light">
                {{ stateOf(phase.state).label }}
              </el-tag>
            </div>
            <div class="tiny">
              {{ fmtTime(phase.startAt) }} ~ {{ fmtTime(phase.endAt) }}
              <span v-if="phase.state === 'ACTIVE'" class="active-tip" :class="{ urgent: isUrgent(phase.endAt) }">
                · 剩余 {{ countdown(phase.endAt) }}
              </span>
            </div>
          </div>
          <div class="phase-actions">
            <el-button size="small" text type="primary" @click="openEdit(phase)">改时间</el-button>
            <el-button size="small" text type="primary" @click="jump(phase)">立即进入</el-button>
            <el-button size="small" text type="danger" :disabled="phase.state === 'CLOSED'" @click="close(phase)">
              立即结束
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 志愿填报 -->
    <el-card shadow="never" class="card card-pad section-card">
      <div class="section-title">
        <h3>志愿填报</h3>
        <span class="muted">三轮各一次，每轮最多 3 个平行志愿</span>
      </div>
      <div class="phase-list">
        <div
          v-for="phase in applyPhases"
          :key="phase.id"
          class="phase-row"
          :class="[phase.state.toLowerCase(), phase.roundNo === round ? 'current-round' : '']"
        >
          <div class="phase-seq">{{ phase.seq }}</div>
          <div class="phase-main">
            <div class="phase-name">
              第 {{ phase.roundNo }} 轮填报
              <el-tag v-if="phase.roundNo === round" size="small" type="primary" effect="plain">当前轮次</el-tag>
              <el-tag size="small" :type="stateOf(phase.state).type" effect="light">
                {{ stateOf(phase.state).label }}
              </el-tag>
            </div>
            <div class="tiny">
              {{ fmtTime(phase.startAt) }} ~ {{ fmtTime(phase.endAt) }}
              <span v-if="phase.state === 'ACTIVE'" class="active-tip" :class="{ urgent: isUrgent(phase.endAt) }">
                · 剩余 {{ countdown(phase.endAt) }}
              </span>
            </div>
          </div>
          <div class="phase-actions">
            <el-button size="small" text type="primary" @click="openEdit(phase)">改时间</el-button>
            <el-button size="small" text type="primary" @click="jump(phase)">立即进入</el-button>
            <el-button size="small" text type="danger" :disabled="phase.state === 'CLOSED'" @click="close(phase)">
              立即结束
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 三轮录取：按轮次折叠 -->
    <el-card shadow="never" class="card card-pad section-card">
      <div class="section-title">
        <h3>三轮录取</h3>
        <span class="muted">点击标题展开该轮的面试与确认</span>
      </div>
      <el-collapse v-model="openRounds" class="accept-collapse">
        <el-collapse-item v-for="group in acceptGroups" :key="group.round" :name="group.round">
          <template #title>
            <div class="acc-head">
              <span class="acc-seq">{{ group.batch.seq }}</span>
              <span class="acc-name" :class="{ 'is-current': group.round === round }">{{ group.batch.name }}</span>
              <el-tag v-if="group.round === round" size="small" type="primary" effect="plain">当前轮次</el-tag>
              <el-tag size="small" :type="stateOf(group.batch.state).type" effect="light">
                {{ stateOf(group.batch.state).label }}
              </el-tag>
              <span class="acc-time">{{ fmtTime(group.batch.startAt) }} ~ {{ fmtTime(group.batch.endAt) }}</span>
              <span
                v-if="group.batch.state === 'ACTIVE'"
                class="active-tip"
                :class="{ urgent: isUrgent(group.batch.endAt) }"
              >
                · 剩余 {{ countdown(group.batch.endAt) }}
              </span>
              <span class="acc-actions">
                <el-button size="small" text type="primary" @click.stop="openEdit(group.batch)">改录取时间</el-button>
                <el-button size="small" text type="primary" @click.stop="jump(group.batch)">立即进入</el-button>
                <el-button
                  size="small"
                  text
                  type="danger"
                  :disabled="group.batch.state === 'CLOSED'"
                  @click.stop="close(group.batch)"
                >
                  立即结束
                </el-button>
              </span>
            </div>
          </template>

          <div class="phase-list inner">
            <div
              v-for="phase in group.items"
              :key="phase.id"
              class="phase-row child"
              :class="phase.state.toLowerCase()"
            >
              <div class="phase-seq">{{ phase.seq }}</div>
              <div class="phase-main">
                <div class="phase-name">
                  {{ phase.name }}
                  <el-tag size="small" :type="stateOf(phase.state).type" effect="light">
                    {{ stateOf(phase.state).label }}
                  </el-tag>
                </div>
                <div class="tiny">
                  {{ fmtTime(phase.startAt) }} ~ {{ fmtTime(phase.endAt) }}
                  <span
                    v-if="phase.state === 'ACTIVE'"
                    class="active-tip"
                    :class="{ urgent: isUrgent(phase.endAt) }"
                  >
                    · 剩余 {{ countdown(phase.endAt) }}
                  </span>
                </div>
              </div>
              <div class="phase-actions">
                <el-button size="small" text type="primary" @click="openEdit(phase)">改时间</el-button>
                <el-button size="small" text type="primary" @click="jump(phase)">立即进入</el-button>
                <el-button size="small" text type="danger" :disabled="phase.state === 'CLOSED'" @click="close(phase)">
                  立即结束
                </el-button>
              </div>
            </div>
            <div v-if="!group.items.length" class="empty">该轮暂无面试与确认阶段</div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- 收尾阶段 -->
    <el-card shadow="never" class="card card-pad section-card">
      <div class="section-title">
        <h3>收尾阶段</h3>
        <span class="muted">统筹调剂与结果公示</span>
      </div>
      <div class="phase-list">
        <div v-for="phase in tailPhases" :key="phase.id" class="phase-row" :class="phase.state.toLowerCase()">
          <div class="phase-seq">{{ phase.seq }}</div>
          <div class="phase-main">
            <div class="phase-name">
              {{ phase.name }}
              <el-tag size="small" :type="stateOf(phase.state).type" effect="light">
                {{ stateOf(phase.state).label }}
              </el-tag>
            </div>
            <div class="tiny">
              {{ fmtTime(phase.startAt) }} ~ {{ fmtTime(phase.endAt) }}
              <span v-if="phase.state === 'ACTIVE'" class="active-tip" :class="{ urgent: isUrgent(phase.endAt) }">
                · 剩余 {{ countdown(phase.endAt) }}
              </span>
            </div>
          </div>
          <div class="phase-actions">
            <el-button size="small" text type="primary" @click="openEdit(phase)">改时间</el-button>
            <el-button size="small" text type="primary" @click="jump(phase)">立即进入</el-button>
            <el-button size="small" text type="danger" :disabled="phase.state === 'CLOSED'" @click="close(phase)">
              立即结束
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="调整阶段时间" width="480px">
      <el-form label-position="top">
        <el-form-item label="阶段">
          <el-input
            :model-value="`${current?.name}（${current?.roundNo ? '第 ' + current.roundNo + ' 轮' : '全局'}）`"
            disabled
          />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startAt" type="datetime" format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endAt" type="datetime" format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
        <p v-if="current?.code === 'SUPPLEMENT'" class="dialog-tip">
          录取时间包含本轮的「面试与初选 → 拟录取确认」。保存后这两段会按原时间比例同步缩放，无需逐条再改。
        </p>
        <p v-else-if="current?.roundNo" class="dialog-tip">
          若调整后的时间超出所属轮次的录取窗口，录取时间会自动扩展以包住面试与确认。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import SelectionTimeline from '../../components/SelectionTimeline.vue'
import { countdown, fmtTime, isUrgent, phaseStateMap } from '../../utils/labels.js'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const resetting = ref(false)
const phases = ref([])
const round = ref(store.currentRound || 1)
const dialogVisible = ref(false)
const current = ref(null)
const form = reactive({ startAt: null, endAt: null })
const openRounds = ref([store.currentRound || 1])

const leadPhases = computed(() =>
  phases.value.filter((p) => !p.roundNo && ['BRIEFING', 'OPEN_DAY'].includes(p.code))
)

const applyPhases = computed(() =>
  phases.value.filter((p) => p.code === 'APPLY').sort((a, b) => a.roundNo - b.roundNo)
)

const tailPhases = computed(() =>
  phases.value.filter((p) => !p.roundNo && ['ADJUST', 'PUBLISH'].includes(p.code))
)

/** 三轮录取：每轮一张折叠卡，卡内为该轮的面试与确认 */
const acceptGroups = computed(() => {
  const groups = []
  for (const r of [1, 2, 3]) {
    const batch = phases.value.find((p) => p.code === 'SUPPLEMENT' && p.roundNo === r)
    if (!batch) continue
    const items = phases.value.filter(
      (p) => p.roundNo === r && ['INTERVIEW', 'CONFIRM'].includes(p.code)
    )
    groups.push({ round: r, batch, items })
  }
  return groups
})

function stateOf(state) {
  return phaseStateMap[state] || { label: state, type: 'info' }
}

function toApi(date) {
  const d = new Date(date)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

async function load() {
  loading.value = true
  try {
    phases.value = await api.phases()
    round.value = store.currentRound || round.value
    if (!openRounds.value.length) openRounds.value = [round.value]
  } finally {
    loading.value = false
  }
}

function openEdit(phase) {
  current.value = phase
  form.startAt = new Date(String(phase.startAt).replace(/-/g, '/'))
  form.endAt = new Date(String(phase.endAt).replace(/-/g, '/'))
  dialogVisible.value = true
}

async function save() {
  if (!form.startAt || !form.endAt) {
    ElMessage.warning('请选择完整时间')
    return
  }
  saving.value = true
  try {
    await api.updatePhase(current.value.id, { startAt: toApi(form.startAt), endAt: toApi(form.endAt) })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
    await store.refreshPhase()
  } finally {
    saving.value = false
  }
}

async function jump(phase) {
  try {
    await ElMessageBox.confirm(
      `立即进入「${phase.name}」？窗口将整体平移到当前时间（保留原时长）${phase.roundNo ? `，并切换当前轮次为第 ${phase.roundNo} 轮` : ''}。`,
      '立即进入阶段',
      { confirmButtonText: '确认进入', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await api.jumpPhase(phase.id)
  ElMessage.success('已进入该阶段')
  await load()
  await store.refreshPhase()
}

async function close(phase) {
  try {
    await ElMessageBox.confirm(`立即结束「${phase.name}」？结束后该阶段的所有写操作会被拒绝。`, '结束阶段', {
      confirmButtonText: '确认结束',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await api.closePhase(phase.id)
  ElMessage.success('已结束该阶段')
  await load()
  await store.refreshPhase()
}

async function changeRound(value) {
  await api.setRound(value)
  ElMessage.success(`当前轮次已切换为第 ${value} 轮`)
  await store.refreshPhase()
  await load()
}

/**
 * 重置整体流程。
 * 不可撤销：要求输入「重置」二次确认，成功后清空志愿/面试/Offer 等业务数据，
 * 时间轴回到「宣讲」，学生可重新填报。
 */
async function resetAll() {
  try {
    await ElMessageBox.prompt(
      '将清空全部志愿、面试评分、拟录取/Offer、面试排期、调剂记录与系统通知，'
        + '所有导师名额恢复到总额，时间轴回到「宣讲」阶段、轮次回到第 1 轮。'
        + '学生账号、档案、简历与私信会保留，重置后可重新填报。此操作不可撤销。',
      '重置整体流程',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: '输入「重置」确认',
        inputValidator: (value) => value === '重置' || '请输入「重置」两个字'
      }
    )
  } catch {
    return
  }

  resetting.value = true
  try {
    const result = await api.resetPhases()
    ElMessage.success(
      `已重置到「${result?.phase?.name || '宣讲'}」，清空 ${result?.applications ?? 0} 条志愿、`
        + `${result?.interviewResults ?? 0} 条面试记录`
    )
    await store.refreshPhase()
    await load()
  } finally {
    resetting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.timeline-block {
  display: block;
  margin-bottom: 14px;
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.section-card {
  margin-bottom: 14px;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.phase-list {
  display: flex;
  flex-direction: column;
}

.phase-list.inner {
  padding: 2px 0 6px;
}

.phase-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--line);
}

.phase-row:last-child {
  border-bottom: none;
}

.phase-row.current-round {
  background: var(--fill-2);
  border-radius: 10px;
  padding-left: 12px;
  padding-right: 12px;
}

.phase-row.child {
  padding-left: 12px;
  padding-top: 12px;
  padding-bottom: 12px;
}

.phase-row.child .phase-seq {
  width: 22px;
  height: 22px;
  font-size: 11px;
}

.phase-seq {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--fill-2);
  color: var(--label-2);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.phase-row.active .phase-seq {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.phase-main {
  flex: 1;
  min-width: 0;
}

.phase-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}

.phase-actions {
  display: flex;
  gap: 2px;
  flex: none;
}

.active-tip {
  color: var(--ios-green-dark);
}

/* 剩余不足 1 小时：时间转红 */
.active-tip.urgent {
  color: var(--ios-red-dark);
  font-weight: 800;
}

/* 三轮录取折叠卡 */
.accept-collapse {
  border-top: none;
}

.accept-collapse :deep(.el-collapse-item__header) {
  height: auto;
  padding: 12px 0;
  line-height: 1.5;
  border-bottom: 1px solid var(--line);
  font-size: inherit;
}

.accept-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid var(--line);
}

.acc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
  padding-right: 8px;
}

.acc-seq {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: var(--fill-2);
  color: var(--label-2);
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.acc-name {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--label-1);
}

.acc-name.is-current {
  color: var(--el-color-primary);
}

.acc-time {
  font-size: 12px;
  color: var(--label-2);
  font-variant-numeric: tabular-nums;
}

.acc-actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
}

.dialog-tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--label-2);
}
</style>
