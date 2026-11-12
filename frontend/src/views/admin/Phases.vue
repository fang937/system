<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>阶段与轮次</h2>
        <p>时间窗口是后端唯一校验依据：前端按钮只是提示，所有写接口都会按这里的配置做绝对时间校验。</p>
      </div>
      <div class="head-tools">
        <el-select v-model="round" size="default" style="width: 140px" @change="changeRound">
          <el-option :value="1" label="当前：第 1 轮" />
          <el-option :value="2" label="当前：第 2 轮" />
          <el-option :value="3" label="当前：第 3 轮" />
        </el-select>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <div class="phase-list">
        <div v-for="phase in phases" :key="phase.id" class="phase-row" :class="phase.state.toLowerCase()">
          <div class="phase-seq">{{ phase.seq }}</div>
          <div class="phase-main">
            <div class="phase-name">
              {{ phase.name }}
              <el-tag size="small" effect="plain">{{ phase.roundNo ? `第 ${phase.roundNo} 轮` : '全局' }}</el-tag>
              <el-tag size="small" :type="stateOf(phase.state).type" effect="light">{{ stateOf(phase.state).label }}</el-tag>
            </div>
            <div class="tiny">
              {{ fmtTime(phase.startAt) }} ~ {{ fmtTime(phase.endAt) }}
              <span v-if="phase.state === 'ACTIVE'" class="active-tip">· 剩余 {{ countdown(phase.endAt) }}</span>
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
          <el-input :model-value="`${current?.name}（${current?.roundNo ? '第 ' + current.roundNo + ' 轮' : '全局'}）`" disabled />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startAt" type="datetime" format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endAt" type="datetime" format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { countdown, fmtTime, phaseStateMap } from '../../utils/labels.js'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const phases = ref([])
const round = ref(store.currentRound || 1)
const dialogVisible = ref(false)
const current = ref(null)
const form = reactive({ startAt: null, endAt: null })

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

onMounted(load)
</script>

<style scoped>
.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.phase-list {
  display: flex;
  flex-direction: column;
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
</style>
