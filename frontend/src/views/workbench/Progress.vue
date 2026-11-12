<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>本室招生进度</h2>
        <p>统筹查看本实训室各导师名额、报考与确认情况，及时发现招不满或超额风险。</p>
      </div>
      <div class="head-tools">
        <el-radio-group v-model="round" size="small" @change="load">
          <el-radio-button :value="1">第 1 轮</el-radio-button>
          <el-radio-button :value="2">第 2 轮</el-radio-button>
          <el-radio-button :value="3">第 3 轮</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat tone-blue">
        <div class="label">总名额</div>
        <div class="value mono">{{ summary.totalQuota ?? 0 }}</div>
        <div class="hint">{{ summary.tutorCount ?? 0 }} 位导师</div>
      </div>
      <div class="stat tone-orange">
        <div class="label">已锁定</div>
        <div class="value mono">{{ summary.lockedQuota ?? 0 }}</div>
        <div class="hint">已发放拟录取</div>
      </div>
      <div class="stat tone-green">
        <div class="label">剩余名额</div>
        <div class="value mono">{{ summary.remainingQuota ?? 0 }}</div>
        <div class="hint">可用于补录 / 调剂</div>
      </div>
      <div class="stat tone-purple">
        <div class="label">已确认</div>
        <div class="value mono">{{ summary.confirmedCount ?? 0 }}</div>
        <div class="hint">学生已点击确认</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <el-tabs v-model="tab">
        <el-tab-pane label="导师进度" name="tutors">
          <el-table v-loading="loading" :data="tutors" stripe>
            <el-table-column label="导师" min-width="140">
              <template #default="{ row }">
                <div class="cell-name">{{ row.tutorName }}</div>
                <div class="tiny">{{ row.title }}</div>
              </template>
            </el-table-column>
            <el-table-column label="报考" width="80" align="center">
              <template #default="{ row }">{{ row.applicantCount }}</template>
            </el-table-column>
            <el-table-column label="名额使用" min-width="220">
              <template #default="{ row }">
                <div class="quota-bar">
                  <div class="quota-track">
                    <div
                      class="quota-fill"
                      :class="'tone-' + usageTone(percent(row))"
                      :style="{ width: percent(row) + '%' }"
                    />
                  </div>
                  <span class="mono">{{ row.quota - row.remainingQuota }}/{{ row.quota }}</span>
                </div>
                <div class="tiny" style="margin-top: 4px">
                  剩余 {{ row.remainingQuota }} · 递补 {{ row.reserveCount }} · 待处理 {{ row.pendingCount }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="拟录取" width="90" align="center">
              <template #default="{ row }">{{ row.offerCount }}</template>
            </el-table-column>
            <el-table-column label="已确认" width="90" align="center">
              <template #default="{ row }">
                <span :class="{ 'text-success': row.confirmedCount > 0 }">{{ row.confirmedCount }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty">本室暂无导师</div>
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="学生去向" name="students">
          <el-table v-loading="loading" :data="students" stripe>
            <el-table-column label="学生" min-width="140">
              <template #default="{ row }">
                <div class="cell-name">{{ row.studentName }}</div>
                <div class="tiny">{{ row.studentNo }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="major" label="专业" min-width="140" />
            <el-table-column prop="tutorName" label="导师" width="110" />
            <el-table-column prop="score" label="成绩" width="80" align="center" />
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="statusOf(row.offerStatus).type" size="small" effect="light">
                  {{ statusOf(row.offerStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="确认" width="110" align="center">
              <template #default="{ row }">
                <span class="tiny">{{ confirmText(row.confirmStatus) }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty">本室暂无录取记录</div>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { offerStatusMap } from '../../utils/labels.js'
import { usageTone } from '../../utils/tones.js'

const store = useUserStore()
const loading = ref(false)
const round = ref(store.currentRound || 1)
const tab = ref('tutors')
const tutors = ref([])
const students = ref([])
const summary = ref({})

function percent(row) {
  if (!row.quota) return 0
  return Math.min(100, Math.round(((row.quota - row.remainingQuota) / row.quota) * 100))
}

function statusOf(value) {
  return offerStatusMap[value ?? 0] || offerStatusMap[0]
}

function confirmText(value) {
  return ['待确认', '已确认', '超时放弃', '已放弃'][value ?? 0] || '—'
}

async function load() {
  loading.value = true
  try {
    const [progress, list] = await Promise.all([api.labProgress(round.value), api.labStudents(round.value)])
    tutors.value = progress.tutors || []
    summary.value = progress.summary || {}
    students.value = list
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}

.text-success {
  color: var(--ios-green-dark);
  font-weight: 600;
}
</style>
