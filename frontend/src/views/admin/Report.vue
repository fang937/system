<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>结果公示</h2>
        <p>录取名单按实训室与导师分组展示，可导出 Excel 归档。</p>
      </div>
      <div class="head-tools">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="label">录取人数</div>
        <div class="value mono">{{ rows.length }}</div>
        <div class="hint">含双选与调剂录取</div>
      </div>
      <div class="stat">
        <div class="label">双选录取</div>
        <div class="value mono">{{ directCount }}</div>
        <div class="hint">正常双选确认</div>
      </div>
      <div class="stat">
        <div class="label">调剂录取</div>
        <div class="value mono">{{ adjustedCount }}</div>
        <div class="hint">室内 / 全院 / 人工</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <el-table v-loading="loading" :data="rows" stripe max-height="620">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="学生" min-width="140">
          <template #default="{ row }">
            <div class="cell-name">{{ row.studentName }}</div>
            <div class="tiny">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="major" label="专业" min-width="150" />
        <el-table-column prop="className" label="班级" width="130" />
        <el-table-column prop="labName" label="实训室" min-width="150" />
        <el-table-column label="导师" width="110">
          <template #default="{ row }">
            {{ row.tutorName }}<span class="tiny"> · {{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="轮次" width="90" align="center">
          <template #default="{ row }">第 {{ row.roundNo }} 轮</template>
        </el-table-column>
        <el-table-column label="成绩" width="80" align="center">
          <template #default="{ row }">{{ row.score ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="录取方式" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.adjustType ? 'warning' : 'success'" effect="plain">
              {{ adjustTypeMap[row.adjustType] || '双选录取' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无录取数据，等待双选或调剂完成</div>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { adjustTypeMap } from '../../utils/labels.js'

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])

const adjustedCount = computed(() => rows.value.filter((row) => row.adjustType).length)
const directCount = computed(() => rows.value.length - adjustedCount.value)

async function load() {
  loading.value = true
  try {
    rows.value = await api.results()
  } finally {
    loading.value = false
  }
}

async function exportExcel() {
  exporting.value = true
  try {
    const blob = await api.exportResults()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `录取名单-${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-tools {
  display: flex;
  gap: 10px;
}

.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}
</style>
