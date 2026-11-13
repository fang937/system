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
        <h3>简历解析引擎</h3>
        <span class="muted">扫描件简历走哪个 OCR 服务，切换后立即生效</span>
      </div>

      <div class="ocr-row">
        <el-radio-group v-model="ocr.provider" :disabled="ocr.saving" @change="saveOcr">
          <el-radio-button value="auto">自动（MinerU 优先）</el-radio-button>
          <el-radio-button value="mineru">仅 MinerU</el-radio-button>
          <el-radio-button value="pp-structure-v3">仅 PP-StructureV3</el-radio-button>
          <el-radio-button value="ocr">仅普通 OCR</el-radio-button>
          <el-radio-button value="none">关闭 OCR</el-radio-button>
        </el-radio-group>
      </div>

      <div class="ocr-available">
        <el-tag :type="ocr.available.mineru ? 'success' : 'info'" effect="light" size="small">
          MinerU {{ ocr.available.mineru ? '已配置 · 首选' : '未配置令牌' }}
        </el-tag>
        <el-tag :type="ocr.available['pp-structure-v3'] ? 'success' : 'info'" effect="light" size="small">
          PP-StructureV3 {{ ocr.available['pp-structure-v3'] ? '已配置 · 副选' : '未配置地址' }}
        </el-tag>
        <el-tag :type="ocr.available.ocr ? 'success' : 'info'" effect="light" size="small">
          普通 OCR {{ ocr.available.ocr ? '已配置' : '未配置' }}
        </el-tag>
        <span v-if="ocr.model" class="tiny">MinerU 模型：{{ ocr.model }}</span>
      </div>

      <p class="ocr-note">
        自动模式按 <b>MinerU → PP-StructureV3 → 普通 OCR</b> 依次尝试，前一个失败自动降级；
        密钥在 <code>.env</code> 配置（<code>MINERU_TOKEN</code> / <code>OCR_STRUCTURE_API_KEY</code>），不在页面暴露。
        带文本层的 PDF 与 DOCX 不经过 OCR，直接读正文。
      </p>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { fmtTime } from '../../utils/labels.js'
import { labTone, usageTone } from '../../utils/tones.js'

const loading = ref(false)
const data = ref({})
const providers = ref([])
const stats = computed(() => data.value.stats || {})

const ocr = reactive({ provider: 'auto', available: {}, model: '', saving: false })

const OCR_LABELS = {
  auto: '自动（MinerU 优先）',
  mineru: '仅 MinerU',
  'pp-structure-v3': '仅 PP-StructureV3',
  ocr: '仅普通 OCR',
  none: '已关闭 OCR'
}

async function loadOcr() {
  try {
    const res = await api.ocrConfig()
    ocr.provider = res?.provider || 'auto'
    ocr.available = res?.available || {}
    ocr.model = res?.model || ''
  } catch (error) {
    ElMessage.error(`读取解析引擎配置失败：${error.message}`)
  }
}

async function saveOcr(value) {
  ocr.saving = true
  try {
    await api.updateOcrConfig(value)
    ElMessage.success(`简历解析引擎：${OCR_LABELS[value] || value}`)
  } catch (error) {
    ElMessage.error(`切换失败：${error.message}`)
    await loadOcr()
  } finally {
    ocr.saving = false
  }
}

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
  loadOcr()
})
</script>

<style scoped>
.ocr-row {
  margin: 2px 0 12px;
}

.ocr-available {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.ocr-note {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.75;
  color: var(--label-3);
}

.ocr-note b {
  color: var(--label-1);
  font-weight: 650;
}

.ocr-note code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--fill-1);
  font-size: 11.5px;
}

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
