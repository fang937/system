<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>AI 优化简历</h2>
        <p>
          上传 PDF / DOCX 简历后，可编辑或导入结构化内容，再选择目标岗位与侧重点生成逐栏建议。原文与建议并排对照，可单条应用，也可一键应用后继续编辑保存。
        </p>
      </div>
      <div class="head-actions">
        <el-button
          :loading="importing"
          :disabled="!draft.resume.uploaded"
          :title="draft.resume.uploaded ? '' : '请先上传简历'"
          @click="importFromResume"
        >
          从已上传简历导入
        </el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="saveDraft">
          {{ dirty ? '保存草稿' : '已保存' }}
        </el-button>
      </div>
    </div>

    <el-alert v-if="!loading && !draft.resume.uploaded" :closable="false" type="warning" show-icon>
      <template #title>尚未上传简历，AI 分析不可用</template>
      <div class="upload-gate">
        <span>请先上传 PDF / DOCX 简历，再使用 AI 优化与「从简历导入」；下方结构化内容仍可手动编辑。</span>
        <el-button size="small" type="primary" plain @click="$router.push('/student/resume')">
          去上传简历
        </el-button>
      </div>
    </el-alert>

    <div v-loading="loading" class="opt-layout">
      <div class="opt-main">
        <section class="card card-pad">
          <div class="section-title">
            <h3>简历内容</h3>
            <span class="count-total" :class="{ over: overTotal }">
              总计 {{ totalChars }} / {{ draft.limits.maxTotalChars }} 字
            </span>
          </div>

          <div class="draft-sections">
            <article
              v-for="(section, index) in draft.sections"
              :id="'draft-' + section.key"
              :key="section.key"
              class="draft-section"
              :class="{
                open: isOpen(section.key),
                over: section.content.length > draft.limits.maxSectionChars
              }"
            >
              <header class="draft-head" @click="toggle(section.key)">
                <span class="draft-index">{{ index + 1 }}</span>
                <div class="draft-main">
                  <div class="draft-title">{{ section.title }}</div>
                  <div class="draft-hint">{{ section.hint }}</div>
                </div>
                <span
                  class="draft-count"
                  :class="{ over: section.content.length > draft.limits.maxSectionChars }"
                >
                  {{ section.content.length }}
                </span>
                <el-icon class="chev"><ArrowDown /></el-icon>
              </header>

              <div v-show="isOpen(section.key)" class="draft-body">
                <el-input
                  v-model="section.content"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 14 }"
                  :placeholder="'填写' + section.title + '…'"
                />
                <div class="draft-foot">
                  <span class="tiny">建议 80~400 字；一栏一件事，长段落拆成要点。</span>
                  <span
                    class="tiny"
                    :class="{ 'over-text': section.content.length > draft.limits.maxSectionChars }"
                  >
                    {{ section.content.length }} / {{ draft.limits.maxSectionChars }}
                  </span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section v-if="result" ref="resultRef" class="card card-pad result-block">
          <div class="section-title">
            <h3>优化建议</h3>
            <div class="result-actions">
              <el-button size="small" :disabled="!applicableCount" @click="applyAll">
                一键应用全部（{{ applicableCount }}）
              </el-button>
              <el-button size="small" type="primary" :disabled="!dirty" @click="saveDraft">
                保存到草稿
              </el-button>
            </div>
          </div>

          <div class="opt-summary">
            <div class="sum-score">
              <b>{{ result.summary?.scoreAfter ?? '—' }}</b>
              <span>优化后预估分</span>
            </div>
            <div class="sum-main">
              <div class="sum-headline">{{ result.summary?.headline }}</div>
              <div class="sum-advice">{{ result.summary?.advice }}</div>
              <div v-if="result.keywordMatches" class="sum-keywords">
                <span v-for="word in result.keywordMatches.matched || []" :key="word" class="kw-hit">
                  已覆盖 {{ word }}
                </span>
                <span v-for="word in result.keywordMatches.missing || []" :key="word" class="kw-miss">
                  待补 {{ word }}
                </span>
              </div>
            </div>
          </div>

          <div v-if="!suggestions.length" class="empty-state">
            本次没有生成可应用的改写建议。可以补充简历内容、调整目标关键词后重新生成。
          </div>

          <article
            v-for="(item, index) in suggestions"
            :key="item.section + '-' + index"
            class="suggestion"
            :class="{ applied: item.applied }"
          >
            <header class="sug-head">
              <div class="sug-title">
                <span class="sug-index">{{ index + 1 }}</span>
                {{ item.sectionTitle }}
                <span v-if="item.applied" class="applied-tag">已应用</span>
              </div>
              <el-checkbox v-model="item.selected" :disabled="item.applied">选择</el-checkbox>
            </header>

            <div class="compare">
              <div class="compare-col before">
                <div class="compare-label">原文</div>
                <pre>{{ item.before }}</pre>
              </div>
              <div class="compare-col after">
                <div class="compare-label">建议</div>
                <pre>{{ item.after }}</pre>
              </div>
            </div>

            <div v-if="item.reasons?.length" class="sug-row">
              <span class="sug-label">改动</span>
              <span v-for="reason in item.reasons" :key="reason" class="sug-chip">{{ reason }}</span>
            </div>
            <div v-if="item.issues?.length" class="sug-row">
              <span class="sug-label">问题</span>
              <span v-for="issue in item.issues" :key="issue" class="sug-chip issue">{{ issue }}</span>
            </div>

            <footer class="sug-foot">
              <span class="tiny">应用后会替换该栏目全文，仍可继续编辑。</span>
              <div class="sug-actions">
                <el-button size="small" :disabled="item.applied" @click="applySuggestion(item)">
                  应用这条
                </el-button>
                <el-button size="small" type="primary" plain :disabled="item.applied" @click="applyAndEdit(item)">
                  应用并编辑
                </el-button>
              </div>
            </footer>
          </article>
        </section>
      </div>

      <aside class="opt-side">
        <section class="ai-card side-card">
          <div class="ai-head">
            <div class="ai-mark">AI</div>
            <div>
              <div class="ai-title">优化设置</div>
              <div class="ai-meta">告诉 AI 目标岗位与侧重点</div>
            </div>
            <span v-if="engine" class="ai-badge">{{ engine }}</span>
          </div>

          <div class="field">
            <div class="field-label">目标岗位 / 方向</div>
            <el-input
              v-model="draft.targetRole"
              maxlength="60"
              placeholder="如：后端开发实习生 / 计算机视觉"
            />
            <button class="link-btn" type="button" @click="fillFromApplication">从我的志愿带入</button>
          </div>

          <div class="field">
            <div class="field-label">
              目标关键词
              <span class="tiny">回车添加，最多 12 个</span>
            </div>
            <div v-if="draft.keywords.length" class="kw-chips">
              <span v-for="word in draft.keywords" :key="word" class="kw-chip">
                {{ word }}
                <button type="button" aria-label="删除关键词" @click="removeKeyword(word)">×</button>
              </span>
            </div>
            <el-input
              v-model="keywordInput"
              maxlength="30"
              placeholder="如 Java、Spring Boot、MySQL"
              @keyup.enter="addKeyword"
              @blur="addKeyword"
            />
          </div>

          <div class="field">
            <div class="field-label">优化侧重点</div>
            <el-checkbox-group v-model="draft.focus" class="focus-group">
              <el-checkbox v-for="item in FOCUS_OPTIONS" :key="item.key" :value="item.key">
                <span class="focus-label">{{ item.label }}</span>
                <span class="focus-desc">{{ item.desc }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <div class="field">
            <div class="field-label">
              优化栏目
              <span class="tiny">已选 {{ selectedSections.length }} 个</span>
            </div>
            <el-checkbox-group v-model="selectedSections" class="section-group">
              <el-checkbox v-for="section in draft.sections" :key="section.key" :value="section.key">
                {{ section.title }}<span v-if="!section.content.trim()" class="tiny">（空）</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <div v-if="error" class="ai-alert">
            <el-icon class="ai-alert-icon"><WarningFilled /></el-icon>
            <div>
              <div class="ai-alert-title">{{ error.title }}</div>
              <div class="ai-alert-sub">{{ error.message }}</div>
            </div>
          </div>

          <el-button
            class="run-btn"
            type="primary"
            size="large"
            :loading="optimizing"
            :disabled="!canOptimize"
            @click="optimize"
          >
            {{ result ? '重新生成建议' : '开始优化' }}
          </el-button>
          <p v-if="!canOptimize && optimizeHint" class="run-hint">{{ optimizeHint }}</p>
          <p class="ai-note">
            AI 只改写措辞与结构，不新增经历；生成后请逐条核对，再决定是否应用。
          </p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/index.js'

const FOCUS_OPTIONS = [
  { key: 'concise', label: '措辞精炼', desc: '去掉口语化与重复表达' },
  { key: 'quantify', label: '突出成果量化', desc: '补充规模、比例、耗时等数据' },
  { key: 'keywords', label: '匹配目标岗位关键词', desc: '自然覆盖目标岗位词汇' },
  { key: 'professional', label: '提升专业性与可读性', desc: '动词开头、要点式表达' }
]

const DEFAULT_LIMITS = { maxSectionChars: 2000, maxTotalChars: 8000, minChars: 30 }

const draft = reactive({
  sections: [],
  targetRole: '',
  keywords: [],
  focus: FOCUS_OPTIONS.map((item) => item.key),
  updatedAt: null,
  source: 'manual',
  resume: { uploaded: false, fileName: '' },
  limits: { ...DEFAULT_LIMITS }
})

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const optimizing = ref(false)
const dirty = ref(false)
const hydrating = ref(false)
const openKeys = ref([])
const selectedSections = ref([])
const keywordInput = ref('')
const error = ref(null)
const result = ref(null)
const suggestions = ref([])
const engine = ref('')
const resultRef = ref(null)
let optimizeSeq = 0

const totalChars = computed(() =>
  draft.sections.reduce((sum, section) => sum + (section.content?.length || 0), 0)
)

const overSection = computed(
  () => draft.sections.find((section) => section.content.length > draft.limits.maxSectionChars) || null
)

const overTotal = computed(() => totalChars.value > draft.limits.maxTotalChars)

const selectedChars = computed(() =>
  draft.sections
    .filter((section) => selectedSections.value.includes(section.key))
    .reduce((sum, section) => sum + (section.content?.trim().length || 0), 0)
)

const canOptimize = computed(
  () =>
    !loading.value &&
    !optimizing.value &&
    !saving.value &&
    draft.resume.uploaded &&
    selectedSections.value.length > 0 &&
    !overSection.value &&
    !overTotal.value &&
    selectedChars.value >= draft.limits.minChars
)

const optimizeHint = computed(() => {
  if (loading.value) return ''
  if (!draft.resume.uploaded) return '请先上传 PDF / DOCX 简历，再使用 AI 分析'
  if (!selectedSections.value.length) return '请至少选择 1 个要优化的栏目'
  if (overSection.value) {
    return `「${overSection.value.title}」超过 ${draft.limits.maxSectionChars} 字，请先精简`
  }
  if (overTotal.value) return `总字数超过 ${draft.limits.maxTotalChars} 字，请先精简`
  if (selectedChars.value < draft.limits.minChars) {
    return `所选栏目共 ${selectedChars.value} 字，至少填写 ${draft.limits.minChars} 字后再优化`
  }
  return ''
})

const applicableCount = computed(
  () => suggestions.value.filter((item) => item.selected && !item.applied).length
)

function markDirty() {
  if (!hydrating.value) dirty.value = true
}

watch(
  () => draft.sections.map((section) => section.content).join('\u0000'),
  markDirty
)
watch(
  [() => draft.targetRole, () => draft.keywords.join('\u0000'), () => draft.focus.join('\u0000')],
  markDirty
)

function isOpen(key) {
  return openKeys.value.includes(key)
}

function toggle(key) {
  const index = openKeys.value.indexOf(key)
  if (index >= 0) {
    openKeys.value.splice(index, 1)
  } else {
    openKeys.value.push(key)
  }
}

function applyDraft(data) {
  hydrating.value = true
  const sections = (data?.sections || []).map((section) => ({
    ...section,
    content: section.content || ''
  }))
  Object.assign(draft, {
    sections,
    targetRole: data?.targetRole || '',
    keywords: data?.keywords || [],
    focus: data?.focus?.length ? data.focus : FOCUS_OPTIONS.map((item) => item.key),
    updatedAt: data?.updatedAt || null,
    source: data?.source || 'manual',
    resume: data?.resume || { uploaded: false, fileName: '' },
    limits: data?.limits || { ...DEFAULT_LIMITS }
  })

  const withContent = sections.filter((section) => section.content.trim())
  selectedSections.value = withContent.length
    ? withContent.map((section) => section.key)
    : sections.slice(0, 3).map((section) => section.key)
  openKeys.value = (withContent.length ? withContent.slice(0, 3) : sections.slice(0, 3)).map(
    (section) => section.key
  )
  dirty.value = false
  nextTick(() => {
    hydrating.value = false
  })
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const data = await api.resumeDraft()
    applyDraft(data)
  } catch (e) {
    error.value = toError(e)
  } finally {
    loading.value = false
  }
}

function addKeyword() {
  const words = keywordInput.value
    .split(/[,，;；]/)
    .map((word) => word.trim())
    .filter(Boolean)
  for (const word of words) {
    if (draft.keywords.length >= 12) break
    if (!draft.keywords.includes(word)) draft.keywords.push(word)
  }
  keywordInput.value = ''
}

function removeKeyword(word) {
  draft.keywords = draft.keywords.filter((item) => item !== word)
}

async function fillFromApplication() {
  try {
    const data = await api.myApplication()
    const first = data?.choices?.[0]
    const role = first?.researchDirection || first?.labName
    if (role) {
      draft.targetRole = role
      ElMessage.success(`已带入「${role}」`)
    } else {
      ElMessage.info('还没有已提交的志愿，可手动填写目标岗位')
    }
  } catch (e) {
    ElMessage.warning(e.message || '读取志愿失败，可手动填写')
  }
}

async function persistDraft(silent = false) {
  if (overSection.value) {
    error.value = {
      title: '内容超长',
      message: `「${overSection.value.title}」超过 ${draft.limits.maxSectionChars} 字，请精简后再保存`
    }
    return false
  }
  if (overTotal.value) {
    error.value = {
      title: '内容超长',
      message: `简历总字数超过 ${draft.limits.maxTotalChars} 字，请精简后再保存`
    }
    return false
  }
  saving.value = true
  error.value = null
  try {
    const data = await api.saveResumeDraft({
      sections: draft.sections.map((section) => ({ key: section.key, content: section.content })),
      targetRole: draft.targetRole,
      keywords: draft.keywords,
      focus: draft.focus
    })
    draft.updatedAt = data?.updatedAt || draft.updatedAt
    dirty.value = false
    if (!silent) ElMessage.success('草稿已保存')
    return true
  } catch (e) {
    error.value = toError(e)
    return false
  } finally {
    saving.value = false
  }
}

function saveDraft() {
  return persistDraft(false)
}

async function optimize() {
  if (!draft.resume.uploaded) {
    ElMessage.warning('请先上传 PDF / DOCX 简历，再使用 AI 分析')
    return
  }
  if (!canOptimize.value) return
  if (navigator.onLine === false) {
    error.value = offlineError()
    return
  }
  optimizing.value = true
  error.value = null
  const seq = ++optimizeSeq
  try {
    const saved = await persistDraft(true)
    if (!saved || seq !== optimizeSeq) return

    const res = await api.aiResumeOptimize({
      sections: selectedSections.value,
      focus: draft.focus,
      targetRole: draft.targetRole,
      keywords: draft.keywords
    })
    if (seq !== optimizeSeq) return
    const data = res?.data || {}
    result.value = data
    suggestions.value = (data.suggestions || []).map((item) => ({
      ...item,
      selected: true,
      applied: false
    }))
    engine.value = engineLabel(res)
    if (!suggestions.value.length) {
      ElMessage.info('本次没有生成可应用的改写建议，可调整内容后重试')
    }
    await nextTick()
    resultRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    error.value = toError(e)
  } finally {
    optimizing.value = false
  }
}

async function applySuggestion(item, options = {}) {
  const section = draft.sections.find((row) => row.key === item.section)
  if (!section) {
    ElMessage.warning('找不到对应栏目，请刷新后重试')
    return false
  }
  const changed = section.content.trim() && section.content !== item.before
  if (changed && !options.force) {
    try {
      await ElMessageBox.confirm(
        `「${item.sectionTitle}」当前内容与生成建议时的原文不同，应用会覆盖当前内容。`,
        '覆盖确认',
        { confirmButtonText: '覆盖应用', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }
  }
  section.content = item.after
  item.applied = true
  item.selected = false
  dirty.value = true
  if (!openKeys.value.includes(item.section)) openKeys.value.push(item.section)
  return true
}

async function applyAndEdit(item) {
  const applied = await applySuggestion(item)
  if (!applied) return
  await nextTick()
  const element = document.getElementById(`draft-${item.section}`)
  element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element?.querySelector('textarea')?.focus()
}

async function applyAll() {
  const list = suggestions.value.filter((item) => item.selected && !item.applied)
  if (!list.length) return
  const changed = list.filter((item) => {
    const section = draft.sections.find((row) => row.key === item.section)
    return section && section.content.trim() && section.content !== item.before
  })
  if (changed.length) {
    try {
      await ElMessageBox.confirm(
        `有 ${changed.length} 个栏目的当前内容与生成建议时不同，一键应用会覆盖这些修改。`,
        '覆盖确认',
        { confirmButtonText: '覆盖应用', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  let count = 0
  for (const item of list) {
    if (await applySuggestion(item, { force: true })) count += 1
  }
  ElMessage.success(`已应用 ${count} 条建议，可继续编辑后保存`)
}

async function importFromResume() {
  if (navigator.onLine === false) {
    error.value = offlineError()
    return
  }
  const hasContent = draft.sections.some((section) => section.content.trim())
  if (hasContent) {
    try {
      await ElMessageBox.confirm(
        '导入会用已上传简历的正文按栏目覆盖当前草稿内容，未保存的修改会丢失。',
        '导入确认',
        { confirmButtonText: '继续导入', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  importing.value = true
  error.value = null
  try {
    const data = await api.importResumeDraft()
    applyDraft(data)
    optimizeSeq += 1
    result.value = null
    suggestions.value = []
    engine.value = ''
    const matched = data?.matchedSections || []
    ElMessage.success(
      matched.length ? `已导入 ${matched.length} 个栏目：${matched.join('、')}` : '已导入简历正文'
    )
    if (data?.truncated) {
      ElMessage.warning('简历正文较长，超出部分已截取；可在各栏目继续补充。')
    }
  } catch (e) {
    error.value = toError(e)
  } finally {
    importing.value = false
  }
}

function engineLabel(res) {
  if (!res) return ''
  if (res.placeholder) return '规则引擎预览'
  return res.provider ? `${res.provider} · ${res.model}` : String(res.model || '模型')
}

function offlineError() {
  return {
    title: '网络不可用',
    message: '当前处于离线状态，草稿已保留在页面。恢复网络后可重试。'
  }
}

function toError(e) {
  if (navigator.onLine === false) return offlineError()
  if (e?.code === 429) {
    return { title: 'AI 额度不足，已暂停优化', message: e.message }
  }
  if (e?.timeout) {
    return {
      title: '优化超时',
      message: '服务端可能仍在处理，草稿已保留。请稍后重试；多次超时可先精简简历内容。'
    }
  }
  if (e?.code === 400 || e?.code === 413) {
    return { title: '内容不符合要求', message: e.message }
  }
  if (/Network Error|Failed to fetch|网络/i.test(e?.message || '')) {
    return {
      title: '网络连接中断',
      message: '请求未完成，草稿已保留在页面。请检查网络后重试。'
    }
  }
  return { title: 'AI 优化失败', message: e?.message || '未知错误，请稍后重试' }
}

onMounted(load)
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.upload-gate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.opt-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.opt-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.opt-side {
  position: sticky;
  top: 86px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-card {
  padding: 18px;
}

.count-total {
  font-size: 12px;
  color: var(--label-3);
  font-variant-numeric: tabular-nums;
}

.count-total.over {
  color: var(--ios-red-dark);
  font-weight: 600;
}

/* ---------------- 栏目编辑 ---------------- */

.draft-sections {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.draft-section {
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: var(--surface-2);
  overflow: hidden;
  transition: border-color 0.2s var(--ease-ios), background-color 0.2s var(--ease-ios);
}

.draft-section.open {
  border-color: rgba(88, 86, 214, 0.24);
  background: var(--surface);
}

.draft-section.over {
  border-color: rgba(255, 59, 48, 0.42);
}

.draft-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
}

.draft-index {
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #4b49b8;
  background: rgba(88, 86, 214, 0.1);
}

.draft-main {
  flex: 1;
  min-width: 0;
}

.draft-title {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--label-1);
}

.draft-hint {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--label-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.draft-count {
  flex: none;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--label-3);
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--fill-2);
}

.draft-count.over {
  color: var(--ios-red-dark);
  background: rgba(255, 59, 48, 0.1);
}

.chev {
  flex: none;
  font-size: 13px;
  color: var(--label-3);
  transition: transform 0.24s var(--ease-ios);
}

.draft-section.open .chev {
  transform: rotate(180deg);
  color: #4b49b8;
}

.draft-body {
  padding: 0 14px 12px;
}

.draft-body :deep(.el-textarea__inner) {
  font-family: var(--font-ios);
  font-size: 13px;
  line-height: 1.75;
  border-radius: 10px;
  background: var(--fill-2);
  box-shadow: none;
}

.draft-body :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(88, 86, 214, 0.28) inset;
  background: var(--surface);
}

.draft-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
}

.over-text {
  color: var(--ios-red-dark);
  font-weight: 600;
}

/* ---------------- 右侧设置 ---------------- */

.field {
  margin-top: 14px;
}

.field-label {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-bottom: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--label-2);
}

.link-btn {
  margin-top: 6px;
  padding: 0;
  border: none;
  background: none;
  font-family: var(--font-ios);
  font-size: 11.5px;
  color: #4b49b8;
  cursor: pointer;
}

.link-btn:hover {
  text-decoration: underline;
}

.kw-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 7px;
}

.kw-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(88, 86, 214, 0.1);
  color: #4b49b8;
  font-size: 11.5px;
}

.kw-chip button {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  line-height: 1;
  color: #4b49b8;
  cursor: pointer;
  opacity: 0.7;
}

.kw-chip button:hover {
  opacity: 1;
}

.focus-group,
.section-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.focus-group :deep(.el-checkbox),
.section-group :deep(.el-checkbox) {
  height: auto;
  min-height: 26px;
  margin-right: 0;
  align-items: flex-start;
}

.focus-group :deep(.el-checkbox__label) {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  padding-top: 2px;
}

.focus-label {
  font-size: 12.5px;
  color: var(--label-1);
}

.focus-desc {
  font-size: 11px;
  color: var(--label-3);
}

.run-btn {
  width: 100%;
  margin-top: 16px;
}

.run-hint {
  margin: 7px 0 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--ios-orange-dark);
}

/* ---------------- 结果 ---------------- */

.result-block {
  scroll-margin-top: 86px;
}

.result-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.opt-summary {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(88, 86, 214, 0.08), rgba(175, 82, 222, 0.04));
}

.sum-score {
  flex: none;
  min-width: 88px;
  text-align: center;
}

.sum-score b {
  display: block;
  font-size: 30px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #4b49b8;
  font-variant-numeric: tabular-nums;
}

.sum-score span {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: var(--label-3);
}

.sum-main {
  flex: 1;
  min-width: 0;
}

.sum-headline {
  font-size: 14px;
  font-weight: 650;
  color: var(--label-1);
}

.sum-advice {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--label-2);
}

.sum-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.kw-hit,
.kw-miss {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
}

.kw-hit {
  background: rgba(52, 199, 89, 0.14);
  color: var(--ios-green-dark);
}

.kw-miss {
  background: rgba(255, 149, 0, 0.15);
  color: var(--ios-orange-dark);
}

.empty-state {
  margin-top: 14px;
  padding: 26px 16px;
  border-radius: 14px;
  background: var(--fill-2);
  text-align: center;
  font-size: 13px;
  color: var(--label-3);
}

.suggestion {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: var(--surface);
}

.suggestion.applied {
  border-color: rgba(52, 199, 89, 0.36);
  background: rgba(52, 199, 89, 0.03);
}

.sug-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.sug-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--label-1);
}

.sug-index {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(150deg, #5856d6, #7d7ae8);
}

.applied-tag {
  font-size: 10.5px;
  font-weight: 500;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: rgba(52, 199, 89, 0.14);
  color: var(--ios-green-dark);
}

.compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.compare-col {
  min-width: 0;
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--fill-2);
}

.compare-col.after {
  background: rgba(88, 86, 214, 0.06);
}

.compare-label {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--label-3);
}

.compare-col.after .compare-label {
  color: #4b49b8;
}

.compare-col pre {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-ios);
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--label-1);
}

.sug-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 9px;
}

.sug-label {
  font-size: 11px;
  color: var(--label-3);
  padding-top: 2px;
}

.sug-chip {
  font-size: 11px;
  line-height: 1.5;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: rgba(88, 86, 214, 0.08);
  color: var(--label-2);
}

.sug-chip.issue {
  background: rgba(255, 149, 0, 0.12);
  color: var(--ios-orange-dark);
}

.sug-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--separator);
}

.sug-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 1080px) {
  .opt-layout {
    grid-template-columns: 1fr;
  }

  .opt-side {
    position: static;
    order: -1;
  }
}

@media (max-width: 700px) {
  .compare {
    grid-template-columns: 1fr;
  }

  .opt-summary {
    flex-direction: column;
  }

  .sum-score {
    text-align: left;
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  .sum-score b {
    font-size: 26px;
  }

  .sug-foot {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
  }
}
</style>
