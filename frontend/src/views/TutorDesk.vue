<template>
  <div class="page">
    <div class="page-title">导师工作台</div>
    <div class="page-sub">{{ me?.labName }} · 名额 {{ me?.quota }} 人，剩余 {{ me?.remainingQuota }} 人。拟录取人数不能超过剩余名额。</div>

    <div class="stat-grid">
      <div class="stat"><div class="label">报考人数</div><div class="value">{{ rows.length }}<small>人</small></div></div>
      <div class="stat"><div class="label">已打分</div><div class="value">{{ rows.filter(r => r.score !== null && r.score !== undefined).length }}<small>人</small></div></div>
      <div class="stat"><div class="label">拟录取</div><div class="value">{{ rows.filter(r => r.offerStatus === 1).length }}<small>人</small></div></div>
      <div class="stat"><div class="label">剩余名额</div><div class="value">{{ me?.remainingQuota ?? '—' }}<small>人</small></div></div>
    </div>

    <div class="card">
      <div class="toolbar" style="margin-bottom: 12px">
        <span style="font-weight: 600">报考名单</span>
        <div style="flex: 1" />
        <el-button size="small" @click="load">刷新</el-button>
      </div>

      <el-table :data="rows" size="small" empty-text="暂时没有学生报考">
        <el-table-column prop="studentNo" label="学号" width="100" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="major" label="专业" min-width="130" />
        <el-table-column prop="className" label="班级" width="90" />
        <el-table-column prop="gpa" label="GPA" width="70" />
        <el-table-column label="志愿序" width="80">
          <template #default="{ row }">第 {{ row.choiceIndex }} 志愿</template>
        </el-table-column>
        <el-table-column label="面试成绩" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row._score" :min="0" :max="100" :precision="1" size="small" controls-position="right" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="offerType(row.offerStatus)">{{ offerText(row.offerStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="saveScore(row)">保存成绩</el-button>
            <el-dropdown style="margin-left: 6px" @command="(cmd) => decide(row, cmd)">
              <el-button size="small" type="primary" plain>定结果<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="1">拟录取</el-dropdown-item>
                  <el-dropdown-item :command="2">递补</el-dropdown-item>
                  <el-dropdown-item :command="3">淘汰</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const me = ref(null)
const rows = ref([])

const offerText = (s) => ({ 0: '未处理', 1: '拟录取', 2: '递补', 3: '淘汰' }[s] ?? '未处理')
const offerType = (s) => ({ 1: 'success', 2: 'warning', 3: 'danger' }[s] ?? 'info')

async function load() {
  const [m, list] = await Promise.all([api.tutorMe(), api.applicants()])
  me.value = m
  rows.value = (list || []).map((r) => ({ ...r, _score: r.score === null || r.score === undefined ? 60 : Number(r.score) }))
}

async function saveScore(row) {
  try {
    await api.score({ applicationId: row.applicationId, studentId: row.studentId, score: row._score, remark: '' })
    ElMessage.success(`已保存 ${row.studentName} 的成绩`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function decide(row, offerStatus) {
  if (!row.resultId) return ElMessage.warning('请先保存面试成绩，系统会生成结果记录')
  try {
    await api.decide({ resultId: row.resultId, offerStatus, rank: offerStatus === 2 ? 1 : null })
    ElMessage.success('已更新录取结果')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>
