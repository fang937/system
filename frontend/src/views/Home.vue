<template>
  <div class="page">
    <div class="page-title">你好，{{ user?.name || user?.username }}</div>
    <div class="page-sub">{{ subtitle }}</div>

    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="stat">
        <div class="label">{{ s.label }}</div>
        <div class="value">{{ s.value }}<small v-if="s.unit">{{ s.unit }}</small></div>
      </div>
    </div>

    <div class="card" v-if="round">
      <div style="display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 14px">
        <div style="font-weight: 600">第 {{ round.roundNo }} 轮 · {{ round.name }}</div>
        <el-tag :type="phaseType" effect="light">当前阶段：{{ phaseText }}</el-tag>
      </div>
      <el-steps :active="phaseIndex" align-center finish-status="success">
        <el-step title="意向填报" :description="fmt(round.applyEnd) + ' 截止'" />
        <el-step title="面试打分" :description="fmt(round.interviewEnd) + ' 截止'" />
        <el-step title="确认 Offer" :description="fmt(round.confirmEnd) + ' 截止'" />
      </el-steps>
    </div>

    <div class="card" style="margin-top: 16px">
      <div style="font-weight: 600; margin-bottom: 10px">快速开始</div>
      <div class="toolbar" style="margin: 0">
        <el-button v-for="b in actions" :key="b.path" type="primary" plain @click="$router.push(b.path)">
          {{ b.label }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'

const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const round = ref(null)
const overview = ref(null)
const stats = ref([])
const now = new Date()

const ROLE_TEXT = {
  STUDENT: '这里是你本轮的双选进度。填报最多 3 个平行志愿，收到多份 Offer 需在截止前确认。',
  TUTOR: '这里是你名下的报考情况。名额有限，拟录取人数不能超过剩余名额。',
  LAB_ADMIN: '这里是本实训室的整体招生进度与面试安排。',
  SYS_ADMIN: '这里是全院双选总览、轮次控制与统筹调剂入口。'
}

const ACTIONS = {
  STUDENT: [
    { label: '查看导师并填报志愿', path: '/tutors' },
    { label: '我的 Offer', path: '/my-selection' }
  ],
  TUTOR: [{ label: '进入导师工作台', path: '/tutor-desk' }],
  LAB_ADMIN: [{ label: '实训室管理', path: '/lab-desk' }],
  SYS_ADMIN: [
    { label: '系统管理', path: '/admin-desk' },
    { label: '实训室进度', path: '/lab-desk' }
  ]
}

const subtitle = computed(() => ROLE_TEXT[user.value?.role] || '')
const actions = computed(() => ACTIONS[user.value?.role] || [])

const fmt = (s) => (s ? String(s).slice(5, 16) : '—')

const phaseIndex = computed(() => {
  if (!round.value) return 0
  const t = now.getTime()
  const at = (v) => new Date(String(v).replace(' ', 'T')).getTime()
  if (t < at(round.value.applyEnd)) return 0
  if (t < at(round.value.interviewEnd)) return 1
  if (t < at(round.value.confirmEnd)) return 2
  return 3
})

const phaseText = computed(() => ['意向填报', '面试打分', '确认 Offer', '本轮结束'][phaseIndex.value])
const phaseType = computed(() => ['primary', 'warning', 'danger', 'info'][phaseIndex.value])

onMounted(async () => {
  try {
    if (user.value?.role === 'SYS_ADMIN') {
      const o = await api.overview()
      overview.value = o
      round.value = o.round
      stats.value = [
        { label: '学生总数', value: o.studentCount, unit: '人' },
        { label: '已填报', value: o.appliedCount, unit: '人' },
        { label: '未填报', value: o.notAppliedCount, unit: '人' },
        { label: '已确认录取', value: o.confirmedCount, unit: '人' },
        { label: '未落实', value: o.unplacedCount, unit: '人' }
      ]
    } else if (user.value?.role === 'STUDENT') {
      const d = await api.myApplication()
      round.value = d?.round
      const offers = await api.myOffers()
      stats.value = [
        { label: '当前轮次', value: d?.round?.roundNo ?? '—', unit: '轮' },
        { label: '我的志愿', value: d?.application ? '已提交' : '未提交' },
        { label: '收到 Offer', value: (offers || []).length, unit: '份' }
      ]
    } else if (user.value?.role === 'TUTOR') {
      const me = await api.tutorMe()
      const list = await api.applicants()
      stats.value = [
        { label: '所属实训室', value: me.labName || '—' },
        { label: '总名额', value: me.quota ?? '—', unit: '人' },
        { label: '剩余名额', value: me.remainingQuota ?? '—', unit: '人' },
        { label: '报考人数', value: (list || []).length, unit: '人' }
      ]
    } else {
      const p = await api.labProgress()
      stats.value = [
        { label: '实训室数', value: (p || []).length, unit: '个' },
        { label: '总名额', value: (p || []).reduce((a, b) => a + Number(b.total_quota || 0), 0), unit: '人' },
        { label: '已录取', value: (p || []).reduce((a, b) => a + Number(b.enrolled || 0), 0), unit: '人' }
      ]
    }
  } catch {
    /* 首页信息失败不影响导航 */
  }
})
</script>
