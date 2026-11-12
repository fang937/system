<template>
  <div class="page">
    <div class="page-title">系统管理</div>
    <div class="page-sub">全院总览、轮次控制、超时扫描与统筹调剂。</div>

    <div class="stat-grid">
      <div class="stat"><div class="label">学生总数</div><div class="value">{{ ov?.studentCount ?? '—' }}<small>人</small></div></div>
      <div class="stat"><div class="label">已填报</div><div class="value">{{ ov?.appliedCount ?? '—' }}<small>人</small></div></div>
      <div class="stat"><div class="label">已出面试结果</div><div class="value">{{ ov?.interviewDoneCount ?? '—' }}<small>人</small></div></div>
      <div class="stat"><div class="label">已确认</div><div class="value">{{ ov?.confirmedCount ?? '—' }}<small>人</small></div></div>
      <div class="stat"><div class="label">未落实</div><div class="value">{{ ov?.unplacedCount ?? '—' }}<small>人</small></div></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="各实训室进度" name="lab">
        <div class="card">
          <el-table :data="ov?.labProgress || []" size="small">
            <el-table-column prop="lab_name" label="实训室" min-width="160" />
            <el-table-column prop="tutor_count" label="导师数" width="90" />
            <el-table-column prop="total_quota" label="总名额" width="90" />
            <el-table-column prop="enrolled" label="已录取" width="90" />
            <el-table-column prop="remaining_quota" label="剩余" width="90" />
            <el-table-column label="完成度" min-width="200">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.progress_pct || 0)" :stroke-width="10" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="轮次控制" name="round">
        <div class="card">
          <el-table :data="rounds" size="small" empty-text="暂无轮次">
            <el-table-column prop="roundNo" label="轮次" width="80" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column label="填报窗口" min-width="230">
              <template #default="{ row }">{{ fmt(row.applyStart) }} ~ {{ fmt(row.applyEnd) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="当前" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.isCurrent" type="success" size="small">进行中</el-tag>
                <span v-else style="color: var(--muted)">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain :disabled="row.isCurrent" @click="switchRound(row)">
                  设为当前
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="自动化与调剂" name="auto">
        <div class="card">
          <div style="font-weight: 600; margin-bottom: 6px">超时未确认扫描</div>
          <div style="color: var(--muted); font-size: 13px; margin-bottom: 12px">
            扫描已过确认截止且仍为「待确认」的 Offer，置为放弃、释放名额，并顺延给递补学生。
            定时任务每分钟也会自动执行一次，这里是手动触发。
          </div>
          <el-button type="warning" plain @click="runSweep">立即扫描</el-button>
        </div>

        <div class="card" style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 6px">统筹调剂</div>
          <div style="color: var(--muted); font-size: 13px; margin-bottom: 12px">
            按「实训室内 → 全院跨室 → 人工兜底」三级优先执行匹配。
          </div>
          <el-button plain @click="preview">预览匹配结果</el-button>
          <el-button type="primary" @click="runAdjust">执行调剂</el-button>
          <pre v-if="adjustResult" class="mono preview">{{ adjustResult }}</pre>
        </div>
      </el-tab-pane>

      <el-tab-pane label="最终名单" name="result">
        <div class="card">
          <el-table :data="finalList" size="small" empty-text="暂无录取数据">
            <el-table-column prop="studentNo" label="学号" width="110" />
            <el-table-column prop="studentName" label="姓名" width="100" />
            <el-table-column prop="tutorName" label="导师" width="110" />
            <el-table-column prop="labName" label="实训室" min-width="160" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const tab = ref('lab')
const ov = ref(null)
const rounds = ref([])
const finalList = ref([])
const adjustResult = ref('')

const fmt = (s) => (s ? String(s).replace('T', ' ').slice(5, 16) : '—')

async function load() {
  const [o, r] = await Promise.all([api.overview(), api.rounds()])
  ov.value = o
  rounds.value = r || []
}

async function loadResult() {
  try {
    finalList.value = (await api.adminResult()) || []
  } catch {
    finalList.value = []
  }
}

async function switchRound(row) {
  await ElMessageBox.confirm(`将第 ${row.roundNo} 轮设为当前轮次？`, '切换轮次', { type: 'warning' })
  try {
    await api.switchRound(row.roundNo)
    ElMessage.success('已切换')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function runSweep() {
  try {
    const r = await api.sweep()
    ElMessage.success(`扫描完成${r ? '：' + JSON.stringify(r) : ''}`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function preview() {
  try {
    const r = await api.adjustPreview()
    adjustResult.value = JSON.stringify(r, null, 2)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function runAdjust() {
  await ElMessageBox.confirm('执行调剂会写入调剂记录并调整名额，确认继续？', '执行调剂', { type: 'warning' })
  try {
    const r = await api.adjustRun()
    adjustResult.value = JSON.stringify(r, null, 2)
    ElMessage.success('调剂执行完成')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(tab, (t) => {
  if (t === 'result' && !finalList.value.length) loadResult()
})

onMounted(load)
</script>

<style scoped>
.preview {
  margin-top: 14px;
  background: #f7f9fc;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
}
</style>
