<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>全局看板</h2>
        <p>学院视角：名额池、填报与录取进度、各实训室招生情况。</p>
      </div>
      <div class="head-tools">
        <el-tag v-if="data.phase?.phase" type="success" effect="light" size="large">
          第 {{ data.phase.round }} 轮 · {{ data.phase.phase.name }}
        </el-tag>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat tone-blue">
        <div class="label">学生总数</div>
        <div class="value mono">{{ stats.studentCount ?? 0 }}</div>
        <div class="hint">已提交志愿 {{ stats.submittedCount ?? 0 }}</div>
      </div>
      <div class="stat tone-purple">
        <div class="label">总名额</div>
        <div class="value mono">{{ stats.totalQuota ?? 0 }}</div>
        <div class="hint">剩余 {{ stats.remainingQuota ?? 0 }}</div>
      </div>
      <div class="stat tone-orange">
        <div class="label">拟录取</div>
        <div class="value mono">{{ stats.offerCount ?? 0 }}</div>
        <div class="hint">递补 {{ stats.reserveCount ?? 0 }} · 淘汰 {{ stats.rejectedCount ?? 0 }}</div>
      </div>
      <div class="stat tone-green">
        <div class="label">已确认</div>
        <div class="value mono">{{ stats.confirmedCount ?? 0 }}</div>
        <div class="hint">最终落定 {{ stats.matchedCount ?? 0 }} 人</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>AI 通道</h3>
        <span class="muted">额度耗尽自动切换到下一通道，也可手动指定首选</span>
      </div>

      <div v-if="!providers.length" class="empty">暂无通道配置</div>
      <div v-else class="provider-list">
        <div
          v-for="item in providers"
          :key="item.name"
          class="provider"
          :class="{
            active: item.active,
            down: item.configured && !item.available,
            unconfigured: !item.configured
          }"
        >
          <span class="provider-dot" />
          <div class="provider-main">
            <div class="provider-name">
              {{ item.name }}
              <span class="ai-tag">{{ item.model || '—' }}</span>
              <span v-if="item.quotaExceeded" class="ai-tag risk">额度不足</span>
            </div>
            <div class="provider-meta">
              {{ providerMeta(item) }}
              <span v-if="item.cooldownUntil"> · 冷却至 {{ String(item.cooldownUntil).slice(11, 16) }}</span>
            </div>
          </div>
          <el-button size="small" :disabled="item.active || !item.configured" @click="switchProvider(item.name)">
            {{ item.active ? '当前' : '切换' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>各实训室招生进度</h3>
        <span class="muted">{{ stats.labCount ?? 0 }} 个实训室 · {{ stats.tutorCount ?? 0 }} 位导师</span>
      </div>
      <el-table v-loading="loading" :data="data.labs || []" stripe>
        <el-table-column label="实训室" min-width="160">
          <template #default="{ row }">
            <span class="lab-name" :class="'tone-' + labTone(row.labId, row.labName)">{{ row.labName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="130" />
        <el-table-column label="导师" width="70" align="center">
          <template #default="{ row }">{{ row.tutorCount }}</template>
        </el-table-column>
        <el-table-column label="报考" width="70" align="center">
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
              <span class="mono">{{ row.lockedQuota }}/{{ row.totalQuota }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="剩余" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ danger: row.remainingQuota === 0 }">{{ row.remainingQuota }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>最新录取动态</h3>
      </div>
      <div v-if="!(data.recentMatches || []).length" class="empty">暂无录取记录</div>
      <div v-for="(item, index) in data.recentMatches || []" :key="index" class="activity">
        <div class="activity-dot" />
        <div class="activity-main">
          <span class="strong">{{ item.studentName }}</span>
          被 <span class="strong">{{ item.tutorName }}</span>（{{ item.labName }}）录取
        </div>
        <span class="tiny">{{ fmtTime(item.matchedAt) }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { fmtTime } from '../../utils/labels.js'
import { labTone, usageTone } from '../../utils/tones.js'

const loading = ref(false)
const data = ref({})
const providers = ref([])
const stats = computed(() => data.value.stats || {})

function percent(row) {
  if (!row.totalQuota) return 0
  return Math.min(100, Math.round((row.lockedQuota / row.totalQuota) * 100))
}

async function load() {
  loading.value = true
  try {
    data.value = await api.adminOverview()
  } finally {
    loading.value = false
  }
}

function providerMeta(item) {
  if (!item.configured) return '未配置 Key'
  if (item.name === 'mock') return '内置规则引擎，无需 Key'
  return item.keyMasked || '已配置'
}

async function loadProviders() {
  try {
    providers.value = await api.aiProviders()
  } catch {
    providers.value = []
  }
}

async function switchProvider(name) {
  await api.aiActivateProvider(name)
  ElMessage.success(`已切换到 ${name} 通道`)
  await loadProviders()
}

onMounted(() => {
  load()
  loadProviders()
})
</script>

<style scoped>
.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lab-name {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.lab-name::before {
  content: '';
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: var(--tone, var(--ios-blue));
}

.danger {
  color: #b42318;
  font-weight: 600;
}

.activity {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  color: var(--ink-2);
}

.activity:last-child {
  border-bottom: none;
}

.activity-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(52, 199, 89, 0.55);
}

.activity-main {
  flex: 1;
}

.strong {
  font-weight: 600;
  color: var(--ink-1);
}
</style>
