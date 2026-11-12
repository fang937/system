<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的录取</h2>
        <p>跟踪已发放 Offer 的确认状态；学生确认后名额才算最终落定。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="card card-pad">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="学生" min-width="140">
          <template #default="{ row }">
            <div class="cell-name">{{ row.studentName }}</div>
            <div class="tiny">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="专业 / 班级" min-width="160">
          <template #default="{ row }">
            {{ row.major || '—' }}
            <div class="tiny">{{ row.className || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="成绩" width="90" align="center">
          <template #default="{ row }">{{ row.score ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.offerStatus).type" size="small" effect="light">
              {{ statusOf(row.offerStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.offerStatus === 1" :type="confirmOf(row.confirmStatus).type" size="small" effect="plain">
              {{ confirmOf(row.confirmStatus).label }}
            </el-tag>
            <span v-else class="tiny">—</span>
          </template>
        </el-table-column>
        <el-table-column label="确认截止" width="150">
          <template #default="{ row }">{{ row.confirmDeadline ? fmtTime(row.confirmDeadline) : '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <span v-if="row.offerStatus === 2" class="tiny">递补顺位第 {{ row.reserveRank || '—' }} 位</span>
            <span v-else-if="row.offerStatus === 1" class="tiny">已锁定 1 个名额</span>
            <span v-else class="tiny">已淘汰，不占名额</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">本轮尚未发出 Offer</div>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { confirmStatusMap, fmtTime, offerStatusMap } from '../../utils/labels.js'

const store = useUserStore()
const loading = ref(false)
const rows = ref([])

function statusOf(value) {
  return offerStatusMap[value ?? 0] || offerStatusMap[0]
}

function confirmOf(value) {
  return confirmStatusMap[value ?? 0] || confirmStatusMap[0]
}

async function load() {
  loading.value = true
  try {
    rows.value = await api.workbenchOffers(store.currentRound)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}
</style>
