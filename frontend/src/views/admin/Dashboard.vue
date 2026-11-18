<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>全局看板</h2>
        <p>学院视角：名额池、填报与录取进度、各实训室招生情况。</p>
      </div>
      <div class="head-tools">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="activePhases.length" :closable="false" type="info" show-icon>
      <template #title>
        当前阶段为：
        <template v-for="(phase, index) in activePhases" :key="phase.id || phase.code + (phase.roundNo ?? '')">
          <span v-if="index > 0" class="phase-sep">；</span>
          <span class="phase-name">{{ phase.name }}</span>
          <span class="muted phase-window">{{ fmtTime(phase.startAt) }} ~ {{ fmtTime(phase.endAt) }}</span>
          <span class="phase-left" :class="{ urgent: isUrgent(phase.endAt) }">
            剩余 {{ countdown(phase.endAt) }}
          </span>
        </template>
      </template>
    </el-alert>

    <div class="stat-grid">
      <div class="stat tone-blue">
        <div class="label">学生总数</div>
        <div class="value mono">{{ stats.studentCount ?? 0 }}</div>
        <div class="hint">累计已提交志愿 {{ stats.submittedCount ?? 0 }}</div>
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
        <div class="hint">累计落定 {{ stats.matchedCount ?? 0 }} 人 · 本轮 {{ stats.roundMatchedCount ?? 0 }} 人</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>AI 通道</h3>
        <span class="muted">额度耗尽自动切换到下一通道；可调试 Key 连通性、切换 Key 下的模型</span>
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
              <el-select
                v-if="item.configured"
                class="model-select"
                size="small"
                :model-value="item.model"
                :loading="modelState[item.name]?.loading"
                placeholder="选择模型"
                filterable
                @visible-change="(open) => open && loadModels(item.name)"
                @change="(model) => saveModel(item.name, model)"
              >
                <el-option v-for="m in modelOptions(item)" :key="m" :label="m" :value="m" />
              </el-select>
              <span v-else class="ai-tag">{{ item.model || '—' }}</span>
              <span v-if="item.quotaExceeded" class="ai-tag risk">额度不足</span>
            </div>
            <div class="provider-meta">
              {{ providerMeta(item) }}
              <span v-if="item.cooldownUntil"> · 冷却至 {{ String(item.cooldownUntil).slice(11, 16) }}</span>
            </div>
            <div v-if="testState[item.name]" class="provider-test" :class="{ ok: testState[item.name].ok }">
              {{ testState[item.name].ok
                ? `调试通过：「${testState[item.name].reply || ''}」 ${testState[item.name].latencyMs ?? '—'} ms`
                : `调试失败：${testState[item.name].error || '未知错误'}` }}
            </div>
          </div>
          <div class="provider-actions">
            <el-button
              size="small"
              :disabled="!item.configured"
              :loading="testing === item.name"
              @click="testProvider(item.name)"
            >
              调试
            </el-button>
            <el-button size="small" :disabled="item.active || !item.configured" @click="switchProvider(item.name)">
              {{ item.active ? '当前' : '切换' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="card card-pad">
      <div class="section-title">
        <h3>API Key 配置</h3>
        <span class="muted">写入后立即生效并保存到数据库（优先于 .env）；页面只显示掩码，不回显完整 Key</span>
      </div>
      <div class="key-rows">
        <div v-for="item in keyProviders" :key="'key-' + item.name" class="key-row">
          <span class="key-label">AI · {{ item.name }}</span>
          <span class="key-mask mono">{{ item.keyMasked || '未配置' }}</span>
          <el-input
            v-model="keyForm[item.name]"
            type="password"
            show-password
            size="small"
            class="key-input"
            placeholder="粘贴该通道的 API Key"
          />
          <el-button
            size="small"
            type="primary"
            plain
            :loading="keySaving === item.name"
            :disabled="!keyForm[item.name]"
            @click="saveAiKey(item.name)"
          >
            保存
          </el-button>
        </div>

        <div class="key-row">
          <span class="key-label">OCR · MinerU Token</span>
          <span class="key-mask mono">{{ ocrSecret('mineru-token') }}</span>
          <el-input
            v-model="ocrKeyForm.mineruToken"
            type="password"
            show-password
            size="small"
            class="key-input"
            placeholder="mineru.net 的 API Token（sk- 开头）"
          />
          <el-button
            size="small"
            type="primary"
            plain
            :loading="keySaving === 'mineru-token'"
            :disabled="!ocrKeyForm.mineruToken"
            @click="saveOcrSecret('mineru-token', ocrKeyForm.mineruToken)"
          >
            保存
          </el-button>
        </div>

        <div class="key-row">
          <span class="key-label">OCR · PP-Structure 地址</span>
          <span class="key-mask mono">{{ ocrSecret('structure-endpoint') || '未配置' }}</span>
          <el-input
            v-model="ocrKeyForm.structureEndpoint"
            size="small"
            class="key-input"
            placeholder="如 https://paddleocr.aistudio-app.com/api/v2/ocr/jobs"
          />
          <el-button
            size="small"
            type="primary"
            plain
            :loading="keySaving === 'structure-endpoint'"
            :disabled="!ocrKeyForm.structureEndpoint"
            @click="saveOcrSecret('structure-endpoint', ocrKeyForm.structureEndpoint)"
          >
            保存
          </el-button>
        </div>

        <div class="key-row">
          <span class="key-label">OCR · PP-Structure Key</span>
          <span class="key-mask mono">{{ ocrSecret('structure-api-key') }}</span>
          <el-input
            v-model="ocrKeyForm.structureKey"
            type="password"
            show-password
            size="small"
            class="key-input"
            placeholder="星河社区的 API Key"
          />
          <el-button
            size="small"
            type="primary"
            plain
            :loading="keySaving === 'structure-api-key'"
            :disabled="!ocrKeyForm.structureKey"
            @click="saveOcrSecret('structure-api-key', ocrKeyForm.structureKey)"
          >
            保存
          </el-button>
        </div>
      </div>
      <p class="ocr-note">
        密钥保存在 <code>system_config</code> 表，重启后仍生效；清空输入框不会删除已保存的 Key，如需停用某个 Key 直接覆盖为新 Key 即可。
      </p>
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
import { useUserStore } from '../../stores/user.js'
import { countdown, fmtTime, isUrgent } from '../../utils/labels.js'
import { labTone, usageTone } from '../../utils/tones.js'

const store = useUserStore()
/** 并行阶段可能不止一条，全部列出，避免「当前阶段」只显示其中一条 */
const activePhases = computed(() =>
  (store.phase?.phases || [])
    .filter((phase) => phase.state === 'ACTIVE')
    .slice()
    .sort((a, b) => (a.seq || 0) - (b.seq || 0) || (a.roundNo || 0) - (b.roundNo || 0))
)
const loading = ref(false)
const data = ref({})
const providers = ref([])
const stats = computed(() => data.value.stats || {})

// AI 通道调试：模型下拉（name -> {loading, loaded, list}）与调试结果（name -> {ok, reply, latencyMs, error}）
const modelState = reactive({})
const testState = reactive({})
const testing = ref('')

// API Key 配置：输入框内容（保存后清空）与保存中状态
const keyForm = reactive({})
const ocrKeyForm = reactive({ mineruToken: '', structureEndpoint: '', structureKey: '' })
const keySaving = ref('')
const keyProviders = computed(() => (providers.value || []).filter((p) => p.name !== 'mock'))

const ocr = reactive({ provider: 'auto', available: {}, model: '', secrets: {}, saving: false })

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
    ocr.secrets = res?.secrets || {}
  } catch (error) {
    ElMessage.error(`读取解析引擎配置失败：${error.message}`)
  }
}

function ocrSecret(target) {
  return ocr.secrets?.[target] || '未配置'
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

function modelOptions(item) {
  const list = modelState[item.name]?.list || []
  // 当前生效模型若不在拉回的列表里（如厂商改名），也保底展示，避免下拉显示空白
  return item.model && !list.includes(item.model) ? [item.model, ...list] : list
}

async function loadModels(name) {
  const state = modelState[name]
  if (state?.loaded || state?.loading) return
  modelState[name] = { loading: true, loaded: false, list: state?.list || [] }
  try {
    const list = await api.aiProviderModels(name)
    modelState[name] = { loading: false, loaded: true, list: Array.isArray(list) ? list : [] }
  } catch (error) {
    modelState[name] = { loading: false, loaded: false, list: [] }
    ElMessage.error(`拉取模型列表失败：${error.message}`)
  }
}

async function saveModel(name, model) {
  try {
    await api.aiSetProviderModel(name, model)
    ElMessage.success(`${name} 模型已切换为 ${model}（已保存，重启后仍生效）`)
    await loadProviders()
  } catch (error) {
    ElMessage.error(`切换模型失败：${error.message}`)
    await loadProviders()
  }
}

async function testProvider(name) {
  testing.value = name
  try {
    const res = await api.aiProviderTest(name)
    testState[name] = res || { ok: false, error: '空响应' }
  } catch (error) {
    testState[name] = { ok: false, error: error.message }
  } finally {
    testing.value = ''
  }
}

async function saveAiKey(name) {
  keySaving.value = name
  try {
    await api.aiSetProviderKey(name, keyForm[name])
    keyForm[name] = ''
    ElMessage.success(`${name} 的 API Key 已写入并立即生效`)
    await loadProviders()
  } catch (error) {
    ElMessage.error(`写入失败：${error.message}`)
  } finally {
    keySaving.value = ''
  }
}

async function saveOcrSecret(target, value) {
  keySaving.value = target
  try {
    await api.updateOcrSecret(target, value)
    if (target === 'mineru-token') ocrKeyForm.mineruToken = ''
    if (target === 'structure-endpoint') ocrKeyForm.structureEndpoint = ''
    if (target === 'structure-api-key') ocrKeyForm.structureKey = ''
    ElMessage.success('已写入并立即生效')
    await loadOcr()
  } catch (error) {
    ElMessage.error(`写入失败：${error.message}`)
  } finally {
    keySaving.value = ''
  }
}

onMounted(() => {
  load()
  loadProviders()
  loadOcr()
})
</script>

<style scoped>
.key-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.key-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.key-label {
  width: 168px;
  flex: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--label-1);
}

.key-mask {
  width: 132px;
  flex: none;
  font-size: 12px;
  color: var(--label-3);
}

.key-input {
  width: 320px;
  max-width: 100%;
  flex: 0 1 320px;
}

.model-select {
  width: 172px;
  flex: none;
}

.provider-actions {
  display: flex;
  flex: none;
  gap: 0;
}

.provider-test {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--label-3);
  word-break: break-all;
}

.provider-test.ok {
  color: var(--ios-green);
}

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

/* 并行阶段提示：名称、时间窗与剩余时间排在同一行，窄屏可自动换行 */
.phase-name {
  font-weight: 600;
}

.phase-window {
  margin-left: 6px;
}

.phase-left {
  margin-left: 6px;
  font-variant-numeric: tabular-nums;
  color: var(--ios-green-dark);
  font-weight: 600;
}

.phase-left.urgent {
  color: var(--ios-red-dark);
  font-weight: 800;
}

.phase-sep {
  margin: 0 4px 0 6px;
  color: var(--ink-300);
}
</style>
