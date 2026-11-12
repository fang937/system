<template>
  <div class="page">
    <div class="page-title">我的双选</div>
    <div class="page-sub">查看本轮志愿、收到的 Offer，并在截止时间前完成确认。</div>

    <div class="card">
      <div style="font-weight: 600; margin-bottom: 12px">本轮志愿</div>
      <template v-if="application">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="轮次">第 {{ application.roundNo }} 轮</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small">{{ appStatusText(application.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmt(application.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="志愿一">{{ tutorName(application.tutor1Id) }}</el-descriptions-item>
          <el-descriptions-item label="志愿二">{{ tutorName(application.tutor2Id) }}</el-descriptions-item>
          <el-descriptions-item label="志愿三">{{ tutorName(application.tutor3Id) }}</el-descriptions-item>
          <el-descriptions-item label="实训室内调剂">{{ application.acceptLabAdjust ? '服从' : '不服从' }}</el-descriptions-item>
          <el-descriptions-item label="全院调剂">{{ application.acceptGlobalAdjust ? '服从' : '不服从' }}</el-descriptions-item>
          <el-descriptions-item label="最终录取">{{ tutorName(application.finalTutorId) || '待定' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <div v-else class="empty">
        本轮还没有提交志愿。
        <el-button type="primary" link @click="$router.push('/tutors')">去填报</el-button>
      </div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div style="font-weight: 600; margin-bottom: 12px">收到的 Offer</div>
      <el-table v-if="offers.length" :data="offers" size="small">
        <el-table-column prop="tutorName" label="导师" width="110" />
        <el-table-column prop="labName" label="实训室" min-width="140" />
        <el-table-column prop="title" label="职称" width="90" />
        <el-table-column label="面试成绩" width="100">
          <template #default="{ row }">{{ row.score ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row)" size="small">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认截止" width="150">
          <template #default="{ row }">{{ fmt(row.confirmDeadline) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.offerStatus === 1 && row.confirmStatus === 0 && row.confirmDeadline">
              <el-button type="primary" size="small" @click="confirm(row)">确认录取</el-button>
              <el-button type="danger" size="small" plain @click="abandon(row)">放弃</el-button>
            </template>
            <span v-else style="color: var(--muted); font-size: 12px">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty">暂未收到 Offer。导师完成面试打分并标记拟录取后会出现在这里。</div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div style="font-weight: 600; margin-bottom: 12px">最终结果</div>
      <el-descriptions v-if="result" :column="3" border size="small">
        <el-descriptions-item label="录取导师">{{ result.tutorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="实训室">{{ result.labName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ result.status || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-else class="empty">结果尚未公布</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const application = ref(null)
const offers = ref([])
const result = ref(null)
const tutors = ref([])

const fmt = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '—')
const tutorName = (id) => {
  if (!id) return ''
  const t = tutors.value.find((x) => String(x.id) === String(id))
  return t ? t.tutorName : `#${id}`
}

const OFFER = { 0: '未处理', 1: '拟录取', 2: '递补', 3: '淘汰' }
const CONFIRM = { 0: '待确认', 1: '已确认', 2: '已放弃' }
const APP_STATUS = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  INTERVIEWING: '面试中',
  RESULT_PUBLISHED: '结果已公布',
  OFFERED: '已录取',
  REJECTED: '未录取',
  CLOSED: '已结束'
}

const appStatusText = (s) => APP_STATUS[s] || s || '—'

function statusText(row) {
  if (row.offerStatus === 1) return CONFIRM[row.confirmStatus] || '待确认'
  return OFFER[row.offerStatus] || '未知'
}

function statusType(row) {
  if (row.offerStatus === 1) return row.confirmStatus === 1 ? 'success' : 'warning'
  if (row.offerStatus === 2) return 'info'
  if (row.offerStatus === 3) return 'danger'
  return 'info'
}

async function load() {
  const [app, of, t] = await Promise.all([api.myApplication(), api.myOffers(), api.tutors()])
  application.value = app?.application || null
  offers.value = of || []
  tutors.value = t || []
  try {
    result.value = await api.myResult()
  } catch {
    result.value = null
  }
}

async function confirm(row) {
  await ElMessageBox.confirm(`确认接受 ${row.tutorName} 的录取？确认后不可撤回。`, '确认录取', { type: 'warning' })
  try {
    await api.confirmOffer(row.resultId)
    ElMessage.success('已确认')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function abandon(row) {
  await ElMessageBox.confirm(`放弃 ${row.tutorName} 的录取？名额将释放给递补学生。`, '放弃录取', { type: 'warning' })
  try {
    await api.abandonOffer(row.resultId)
    ElMessage.success('已放弃')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>
