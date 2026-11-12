<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的 Offer</h2>
        <p>仅收到 1 个 Offer 时系统自动确认；收到 2 个及以上需在确认阶段内选择一个，逾期视为放弃。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="!confirmOpen" :closable="false" type="info" show-icon>
      <template #title>
        当前不在「拟录取确认」阶段{{ confirmPhase ? `（开放时间 ${fmtTime(confirmPhase.startAt)} ~ ${fmtTime(confirmPhase.endAt)}）` : '' }}，
        此阶段才能确认 Offer。
      </template>
    </el-alert>
    <el-alert v-else :closable="false" type="success" show-icon>
      <template #title>
        确认阶段进行中，截止 {{ fmtTime(confirmPhase.endAt) }}，剩余 {{ countdown(confirmPhase.endAt) }}
      </template>
    </el-alert>

    <div v-loading="loading" class="offer-list">
      <el-card v-for="offer in offers" :key="offer.id" shadow="never" class="offer-card">
        <div class="offer-top">
          <div class="offer-head">
            <div class="avatar">{{ offer.tutorName.slice(0, 1) }}</div>
            <div>
              <div class="offer-title">
                {{ offer.tutorName }}
                <span class="muted">· {{ offer.title }}</span>
              </div>
              <div class="tiny">{{ offer.labName }} · {{ offer.researchDirection || '—' }}</div>
            </div>
          </div>
          <div class="offer-tags">
            <el-tag :type="offerStatus(offer).type" effect="light">{{ offerStatus(offer).label }}</el-tag>
            <el-tag v-if="offer.offerStatus === 1" :type="confirmStatus(offer).type" effect="plain">
              {{ confirmStatus(offer).label }}
            </el-tag>
          </div>
        </div>

        <div class="offer-meta">
          <div><span class="muted">轮次</span>{{ roundLabel(offer.roundNo) }}</div>
          <div><span class="muted">面试成绩</span>{{ offer.score ?? '—' }}</div>
          <div v-if="offer.reserveRank"><span class="muted">递补顺位</span>第 {{ offer.reserveRank }} 位</div>
          <div v-if="offer.offerStatus === 1"><span class="muted">确认截止</span>{{ fmtTime(offer.confirmDeadline) }}</div>
          <div v-if="offer.confirmStatus === 1"><span class="muted">确认时间</span>{{ fmtTime(offer.confirmTime) }}</div>
        </div>

        <div v-if="canAct(offer)" class="offer-actions">
          <span class="tiny">剩余 {{ countdown(offer.confirmDeadline) }}</span>
          <div class="spacer" />
          <el-button :loading="acting === offer.id" @click="decline(offer)">放弃</el-button>
          <el-button type="primary" :loading="acting === offer.id" @click="confirm(offer)">接受该 Offer</el-button>
        </div>
      </el-card>

      <div v-if="!loading && !offers.length" class="empty">暂无 Offer，等待导师初选结果</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
import { confirmStatusMap, countdown, fmtTime, offerStatusMap, roundLabel } from '../../utils/labels.js'

const store = useUserStore()
const loading = ref(false)
const acting = ref(null)
const offers = ref([])

const confirmPhase = computed(() => {
  const phases = store.phase?.phases || []
  return phases.find((p) => p.code === 'CONFIRM' && p.roundNo === store.currentRound) || null
})
const confirmOpen = computed(() => confirmPhase.value?.state === 'ACTIVE')

function offerStatus(offer) {
  if (offer.offerStatus === 2) return offerStatusMap[2]
  if (offer.offerStatus === 3) return offerStatusMap[3]
  return offerStatusMap[1]
}

function confirmStatus(offer) {
  return confirmStatusMap[offer.confirmStatus] || confirmStatusMap[0]
}

function canAct(offer) {
  return confirmOpen.value && offer.offerStatus === 1 && offer.confirmStatus === 0
}

async function load() {
  loading.value = true
  try {
    offers.value = await api.myOffers()
  } finally {
    loading.value = false
  }
}

async function confirm(offer) {
  try {
    await ElMessageBox.confirm(
      `确认接受 ${offer.tutorName} 的 Offer？确认后本轮其他 Offer 将自动失效。`,
      '确认拟录取',
      { confirmButtonText: '确认接受', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  acting.value = offer.id
  try {
    await api.confirmOffer(offer.id)
    ElMessage.success('已确认，双选完成')
    await load()
  } finally {
    acting.value = null
  }
}

async function decline(offer) {
  try {
    await ElMessageBox.confirm('放弃后名额将顺延给递补同学，且无法撤销。', '放弃 Offer', {
      confirmButtonText: '确认放弃',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  acting.value = offer.id
  try {
    await api.declineOffer(offer.id)
    ElMessage.success('已放弃该 Offer')
    await load()
  } finally {
    acting.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.offer-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.offer-card {
  border-radius: var(--radius-lg);
}

.offer-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.offer-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.offer-title {
  font-size: 15px;
  font-weight: 600;
}

.offer-tags {
  display: flex;
  gap: 8px;
}

.offer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
  font-size: 13px;
  color: var(--ink-2);
}

.offer-meta .muted {
  margin-right: 6px;
}

.offer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.spacer {
  flex: 1;
}
</style>
