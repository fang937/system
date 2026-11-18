<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>学生名册</h2>
        <p>全部学生账号与当前志愿状态。学号即登录账号，密码见部署说明。</p>
      </div>
      <el-input
        v-model="keyword"
        placeholder="搜学号 / 姓名 / 班级"
        clearable
        style="width: 240px"
        :prefix-icon="Search"
      />
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="label">学生总数</div>
        <div class="value mono">{{ rows.length }}</div>
        <div class="hint">含未填报学生</div>
      </div>
      <div class="stat">
        <div class="label">本轮已填报</div>
        <div class="value mono">{{ submitted }}</div>
        <div class="hint">第 {{ round }} 轮志愿</div>
      </div>
      <div class="stat">
        <div class="label">已录取</div>
        <div class="value mono">{{ matched }}</div>
        <div class="hint">含各轮与调剂</div>
      </div>
      <div class="stat">
        <div class="label">未录取</div>
        <div class="value mono">{{ rows.length - matched }}</div>
        <div class="hint">未填报 {{ notApplied }} · 往轮落选 {{ pastUnmatched }}</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <el-table v-loading="loading" :data="filtered" stripe height="560">
        <el-table-column label="学号" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.studentNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="130">
          <template #default="{ row }">
            <span class="cell-name">{{ row.studentName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="major" label="专业" min-width="110" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column label="GPA" width="90" align="center">
          <template #default="{ row }">{{ row.gpa ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="志愿状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(row).type" effect="light">{{ statusOf(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最终去向" min-width="220">
          <template #default="{ row }">
            <template v-if="row.finalTutorName">
              <el-tag v-if="row.matchedRoundNo" size="small" effect="plain" class="round-tag">
                第 {{ row.matchedRoundNo }} 轮
              </el-tag>
              {{ row.finalLabName }} · {{ row.finalTutorName }}
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">没有匹配的学生</div>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'

const store = useUserStore()
const loading = ref(false)
const rows = ref([])
const keyword = ref('')
const round = computed(() => store.currentRound || 1)

// 后端返回的 matched / appliedThisRound 是 0/1，跨轮录取的学生依然算「已录取」
const submitted = computed(() => rows.value.filter((r) => Number(r.appliedThisRound) === 1).length)
const matched = computed(() => rows.value.filter((r) => Number(r.matched) === 1).length)
// 完全没有志愿记录才算未填报；有志愿但未被录取的是「往轮落选」，不能混为一谈
const notApplied = computed(() => rows.value.filter((r) => !r.applicationId).length)
const pastUnmatched = computed(
  () => rows.value.filter((r) => r.applicationId && Number(r.matched) !== 1 && Number(r.appliedThisRound) !== 1).length
)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((r) =>
    [r.studentNo, r.studentName, r.className, r.major]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(kw))
  )
})

function statusOf(row) {
  if (Number(row.matched) === 1) return { label: '已录取', type: 'success' }
  if (Number(row.appliedThisRound) === 1) return { label: '已填报', type: 'primary' }
  if (row.applicationId) return { label: '往轮未录取', type: 'warning' }
  return { label: '未填报', type: 'info' }
}

async function load() {
  loading.value = true
  try {
    rows.value = await api.students()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cell-name {
  font-weight: 500;
}

.round-tag {
  margin-right: 6px;
}
</style>
