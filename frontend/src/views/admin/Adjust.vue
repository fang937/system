<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>统筹调剂</h2>
        <p>三轮结束后仍未录取的学生进入调剂池，按「实训室内 → 全院跨室 → 人工兜底」三级顺序处理。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="!adjustOpen" :closable="false" type="info" show-icon>
      <template #title>当前不在「统筹调剂」阶段，自动与人工调剂均不可执行。</template>
    </el-alert>

    <div class="stat-grid">
      <div class="stat">
        <div class="label">待调剂学生</div>
        <div class="value mono">{{ students.length }}</div>
        <div class="hint">第 {{ round }} 轮未录取</div>
      </div>
      <div class="stat">
        <div class="label">剩余名额</div>
        <div class="value mono">{{ totalRemaining }}</div>
        <div class="hint">{{ vacancies.length }} 位导师仍有名额</div>
      </div>
      <div class="stat tone-orange">
        <div class="label">需人工处理</div>
        <div class="value mono">{{ manualCount }}</div>
        <div class="hint">不服从调剂，自动调剂会跳过</div>
      </div>
      <div class="stat">
        <div class="label">三级顺序</div>
        <div class="value" style="font-size: 15px; line-height: 1.6">室内 → 全院 → 人工</div>
        <div class="hint">按优先级分数从高到低匹配</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>自动调剂</h3>
      </div>
      <div class="run-row">
        <el-button type="primary" plain :disabled="!adjustOpen" :loading="running === 1" @click="run(1)">
          执行实训室内调剂
        </el-button>
        <el-button type="primary" :disabled="!adjustOpen" :loading="running === 2" @click="run(2)">
          执行全院调剂（含室内优先）
        </el-button>
        <span class="tiny">算法会为每个学生优先匹配其填报过的实训室，再退化为全院任意仍有名额的导师。</span>
      </div>
      <el-alert v-if="lastResult" class="result" :closable="false" type="success" show-icon>
        <template #title>
          本轮执行完成：室内调剂 {{ lastResult.labAdjusted }} 人，全院调剂 {{ lastResult.globalAdjusted }} 人，剩余未匹配
          {{ lastResult.unmatchedLeft }} 人<template v-if="lastResult.manualPending">
            ；其中 <strong>{{ lastResult.manualPending }}</strong> 人不服从调剂，已跳过自动调剂，等待人工处理</template
          >
        </template>
      </el-alert>
      <p class="run-note">
        被志愿导师淘汰后，<b>不服从任何调剂</b>的学生不会进入自动调剂，只会留在调剂池等待管理员人工约谈分配。
      </p>
    </el-card>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>调剂池</h3>
        <span class="muted">人工兜底可指定任意仍有名额的导师</span>
      </div>
      <el-table v-loading="loading" :data="students" stripe>
        <el-table-column label="学生" min-width="140">
          <template #default="{ row }">
            <div class="cell-name">{{ row.studentName }}</div>
            <div class="tiny">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="major" label="专业" min-width="150" />
        <el-table-column label="GPA / 优先级" width="130" align="center">
          <template #default="{ row }">
            <div>{{ row.gpa ?? '—' }}</div>
            <div class="tiny">权重 {{ row.priorityScore ?? '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="appliedLabs" label="填报过的实训室" min-width="180" show-overflow-tooltip />
        <el-table-column label="意愿" width="150" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.acceptLabAdjust ? 'success' : 'info'" effect="plain">室内</el-tag>
            <el-tag size="small" :type="row.acceptGlobalAdjust ? 'success' : 'info'" effect="plain" style="margin-left: 4px">
              全院
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="自动调剂" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.autoAdjustable) === 1" size="small" type="success" effect="light">可自动</el-tag>
            <el-tag v-else size="small" type="warning" effect="light">需人工</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="!adjustOpen" @click="openManual(row)">
              人工分配
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">调剂池为空</div>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="人工兜底分配" width="480px">
      <div v-if="current" class="manual-head">
        <div class="cell-name">{{ current.studentName }}</div>
        <div class="tiny">{{ current.studentNo }} · {{ current.major }}</div>
      </div>
      <el-form label-position="top">
        <el-form-item label="分配导师">
          <el-select v-model="manual.tutorId" filterable placeholder="选择仍有名额的导师" style="width: 100%">
            <el-option
              v-for="item in vacancies"
              :key="item.tutorId"
              :label="`${item.tutorName} · ${item.labName}（余 ${item.remainingQuota}）`"
              :value="item.tutorId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="约谈记录">
          <el-input v-model="manual.reason" type="textarea" :rows="2" placeholder="如：电话约谈，学生同意调剂至该导师" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitManual">确认分配</el-button>
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

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const running = ref(0)
const students = ref([])
const vacancies = ref([])
const round = ref(store.currentRound || 1)
const lastResult = ref(null)
const dialogVisible = ref(false)
const current = ref(null)
const manual = reactive({ tutorId: null, reason: '' })

const adjustOpen = computed(() => store.currentPhase?.code === 'ADJUST' && store.currentPhase?.state === 'ACTIVE')
const totalRemaining = computed(() => vacancies.value.reduce((sum, item) => sum + (item.remainingQuota || 0), 0))

// 不服从任何调剂的学生：自动调剂不会处理，需管理员人工约谈分配
const manualCount = computed(() => students.value.filter((s) => Number(s.autoAdjustable) !== 1).length)

async function load() {
  loading.value = true
  try {
    const data = await api.adjustPreview()
    students.value = data.students || []
    vacancies.value = data.vacancies || []
    round.value = data.round || 1
  } finally {
    loading.value = false
  }
}

async function run(scope) {
  const label = scope === 1 ? '实训室内调剂' : '全院调剂'
  try {
    await ElMessageBox.confirm(`执行${label}？系统会按优先级从高到低自动匹配并立即占用名额。`, '执行自动调剂', {
      confirmButtonText: '执行',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  running.value = scope
  try {
    lastResult.value = await api.adjustRun(scope)
    ElMessage.success('执行完成')
    await load()
  } finally {
    running.value = 0
  }
}

function openManual(row) {
  current.value = row
  manual.tutorId = null
  manual.reason = ''
  dialogVisible.value = true
}

async function submitManual() {
  if (!manual.tutorId) {
    ElMessage.warning('请选择导师')
    return
  }
  saving.value = true
  try {
    await api.adjustManual({
      studentId: current.value.studentId,
      tutorId: manual.tutorId,
      reason: manual.reason
    })
    ElMessage.success('已分配')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.run-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.run-note {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--label-2);
}

.run-note b {
  color: var(--ios-orange-dark);
  font-weight: 650;
}

.result {
  margin-top: 14px;
}

.manual-head {
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--line);
}

.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}
</style>
