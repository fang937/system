<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>面试排期</h2>
        <p>实训室统一面试由管理员排期，导师可在名单页录入成绩。</p>
      </div>
      <el-button type="primary" :icon="Plus" :disabled="!interviewOpen" @click="openDialog()">新增排期</el-button>
    </div>

    <el-alert v-if="!interviewOpen" :closable="false" type="info" show-icon>
      <template #title>当前不在「面试与初选」阶段，排期仅可查看。</template>
    </el-alert>

    <el-card shadow="never" class="card card-pad">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="日期" width="130">
          <template #default="{ row }">{{ row.interviewDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">
            {{ (row.startTime || '').slice(0, 5) }} - {{ (row.endTime || '').slice(0, 5) }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="150" />
        <el-table-column label="面试导师" min-width="130">
          <template #default="{ row }">{{ row.tutorName || '全室统一' }}</template>
        </el-table-column>
        <el-table-column label="容量" width="90" align="center">
          <template #default="{ row }">{{ row.capacity ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :disabled="!interviewOpen" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" text type="danger" :disabled="!interviewOpen" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">尚未安排面试场次</div>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑排期' : '新增排期'" width="480px">
      <el-form label-position="top">
        <el-form-item label="面试日期">
          <el-date-picker v-model="form.interviewDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时间段">
          <div class="time-row">
            <el-time-select v-model="form.startTime" start="08:00" step="00:30" end="20:00" placeholder="开始" />
            <span class="muted">至</span>
            <el-time-select v-model="form.endTime" start="08:00" step="00:30" end="21:00" placeholder="结束" />
          </div>
        </el-form-item>
        <el-form-item label="面试导师（不选则视为全室统一面试）">
          <el-select v-model="form.tutorId" clearable placeholder="选择导师" style="width: 100%">
            <el-option v-for="tutor in tutors" :key="tutor.tutorId" :label="tutor.tutorName" :value="tutor.tutorId" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="如：实训楼 A-301" />
        </el-form-item>
        <el-form-item label="容量（人）">
          <el-input-number v-model="form.capacity" :min="1" :max="200" style="width: 100%" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const tutors = ref([])
const dialogVisible = ref(false)
const form = reactive({
  id: null,
  interviewDate: '',
  startTime: '09:00',
  endTime: '11:00',
  tutorId: null,
  location: '',
  capacity: 10
})

const interviewOpen = computed(() => store.currentPhase?.code === 'INTERVIEW' && store.currentPhase?.state === 'ACTIVE')

async function load() {
  loading.value = true
  try {
    rows.value = await api.schedules(store.currentRound)
    tutors.value = await api.allTutors()
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      interviewDate: row.interviewDate,
      startTime: (row.startTime || '').slice(0, 5) || '09:00',
      endTime: (row.endTime || '').slice(0, 5) || '11:00',
      tutorId: row.tutorId,
      location: row.location,
      capacity: row.capacity || 10
    })
  } else {
    Object.assign(form, {
      id: null,
      interviewDate: '',
      startTime: '09:00',
      endTime: '11:00',
      tutorId: null,
      location: '',
      capacity: 10
    })
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.interviewDate) {
    ElMessage.warning('请选择面试日期')
    return
  }
  saving.value = true
  try {
    const payload = {
      tutorId: form.tutorId,
      interviewDate: form.interviewDate,
      startTime: form.startTime,
      endTime: form.endTime,
      location: form.location,
      capacity: form.capacity
    }
    if (form.id) {
      await api.updateSchedule(form.id, payload)
    } else {
      await api.createSchedule(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除该面试场次？', '删除排期', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await api.deleteSchedule(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.time-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
</style>
