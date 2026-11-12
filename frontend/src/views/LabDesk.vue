<template>
  <div class="page">
    <div class="page-title">实训室管理</div>
    <div class="page-sub">查看本实训室招生进度与报考情况，安排面试场次。</div>

    <div class="card">
      <div style="font-weight: 600; margin-bottom: 12px">招生进度</div>
      <el-table :data="progress" size="small">
        <el-table-column prop="lab_name" label="实训室" min-width="160" />
        <el-table-column prop="tutor_count" label="导师数" width="90" />
        <el-table-column prop="total_quota" label="总名额" width="90" />
        <el-table-column prop="enrolled" label="已录取" width="90" />
        <el-table-column prop="remaining_quota" label="剩余" width="90" />
        <el-table-column label="完成度" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progress_pct || 0)" :stroke-width="10" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card" style="margin-top: 16px">
      <div style="font-weight: 600; margin-bottom: 12px">报考情况</div>
      <el-table :data="applications" size="small" empty-text="暂无报考记录">
        <el-table-column prop="studentNo" label="学号" width="100" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="major" label="专业" min-width="130" />
        <el-table-column prop="tutorName" label="志愿导师" width="110" />
        <el-table-column label="志愿序" width="90">
          <template #default="{ row }">第 {{ row.choiceIndex }} 志愿</template>
        </el-table-column>
        <el-table-column prop="score" label="成绩" width="80" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeOf(row.offerStatus)">{{ textOf(row.offerStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="toolbar" style="margin-bottom: 12px">
        <span style="font-weight: 600">面试安排</span>
        <div style="flex: 1" />
        <el-button size="small" type="primary" plain @click="dialog = true">新增场次</el-button>
      </div>
      <el-table :data="schedules" size="small" empty-text="尚未安排面试">
        <el-table-column prop="interviewDate" label="日期" width="120" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="140" />
        <el-table-column prop="capacity" label="容量" width="80" />
        <el-table-column prop="roundNo" label="轮次" width="80" />
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新增面试场次" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="日期">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始">
          <el-time-picker v-model="form.start" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束">
          <el-time-picker v-model="form.end" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="如 实验楼 A301" />
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="form.capacity" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="createSchedule">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const progress = ref([])
const applications = ref([])
const schedules = ref([])
const dialog = ref(false)
const form = reactive({ date: '', start: '', end: '', location: '', capacity: 20 })

const textOf = (s) => ({ 0: '未处理', 1: '拟录取', 2: '递补', 3: '淘汰' }[s] ?? '未处理')
const typeOf = (s) => ({ 1: 'success', 2: 'warning', 3: 'danger' }[s] ?? 'info')

async function load() {
  const [p, a, s] = await Promise.allSettled([api.labProgress(), api.labApplications(), api.labSchedules()])
  progress.value = p.status === 'fulfilled' ? p.value || [] : []
  applications.value = a.status === 'fulfilled' ? a.value || [] : []
  schedules.value = s.status === 'fulfilled' ? s.value || [] : []
}

async function createSchedule() {
  if (!form.date || !form.start || !form.end) return ElMessage.warning('请填写完整时间')
  try {
    await api.createSchedule({
      interviewDate: form.date,
      startTime: form.start,
      endTime: form.end,
      location: form.location,
      capacity: form.capacity
    })
    ElMessage.success('已新增')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>
