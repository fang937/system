<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>室内调剂</h2>
        <p>针对报过本实训室但未被录取、且服从实训室内调剂的学生，分配给本室仍有名额的导师。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="!adjustOpen" :closable="false" type="info" show-icon>
      <template #title>当前不在「统筹调剂」阶段，室内调剂暂不可操作。</template>
    </el-alert>

    <div class="vacancy-row">
      <div v-for="vacancy in vacancies" :key="vacancy.tutorId" class="vacancy">
        <div>
          <div class="vacancy-name">{{ vacancy.tutorName }}<span class="muted"> · {{ vacancy.title }}</span></div>
          <div class="tiny">剩余名额 {{ vacancy.remainingQuota }} / {{ vacancy.quota }}</div>
        </div>
        <span class="vacancy-count">{{ vacancy.remainingQuota }}</span>
      </div>
      <div v-if="!vacancies.length" class="empty" style="width: 100%">本室暂无剩余名额</div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>待调剂学生</h3>
        <span class="muted">共 {{ pool.length }} 人，按优先级排序</span>
      </div>

      <el-table v-loading="loading" :data="pool" stripe>
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
        <el-table-column label="调剂意愿" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.acceptLabAdjust ? 'success' : 'info'" effect="plain">
              {{ row.acceptLabAdjust ? '服从室内' : '不服从' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分配给" min-width="190">
          <template #default="{ row }">
            <el-select v-model="picks[row.studentId]" size="small" placeholder="选择导师" filterable>
              <el-option
                v-for="vacancy in vacancies"
                :key="vacancy.tutorId"
                :label="`${vacancy.tutorName}（余 ${vacancy.remainingQuota}）`"
                :value="vacancy.tutorId"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="!adjustOpen || !picks[row.studentId]" @click="assign(row)">
              调剂
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无待调剂学生</div>
        </template>
      </el-table>
    </el-card>
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
const pool = ref([])
const vacancies = ref([])
const picks = reactive({})

const adjustOpen = computed(() => store.currentPhase?.code === 'ADJUST' && store.currentPhase?.state === 'ACTIVE')

async function load() {
  loading.value = true
  try {
    const [poolList, vacancyList] = await Promise.all([api.labPool(), api.labVacancies()])
    pool.value = poolList
    vacancies.value = vacancyList
  } finally {
    loading.value = false
  }
}

async function assign(row) {
  const tutorId = picks[row.studentId]
  try {
    await ElMessageBox.confirm(
      `将 ${row.studentName} 调剂至所选导师？调剂后立即占用名额并通知学生。`,
      '室内调剂确认',
      { confirmButtonText: '确认调剂', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await api.labAdjust({ studentId: row.studentId, tutorId, reason: '实训室内调剂' })
  ElMessage.success('调剂成功')
  delete picks[row.studentId]
  await load()
}

onMounted(load)
</script>

<style scoped>
.vacancy-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.vacancy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface);
  border: none;
  border-radius: var(--radius-md);
  padding: 14px 16px;
  box-shadow: var(--shadow-xs), 0 8px 20px rgba(0, 0, 0, 0.04);
}

.vacancy-name {
  font-size: 13.5px;
  font-weight: 500;
}

.vacancy-count {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-color-primary);
  font-variant-numeric: tabular-nums;
}

.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}
</style>
