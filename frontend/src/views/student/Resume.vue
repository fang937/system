<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的简历</h2>
        <p>上传一份 PDF / DOC / DOCX 简历。导师在面试前可查看，教师端的 AI 初评也会把它作为参考。</p>
      </div>
      <el-tag :type="meta.uploaded ? 'success' : 'info'" effect="light" size="large">
        {{ meta.uploaded ? '已上传' : '未上传' }}
      </el-tag>
    </div>

    <!-- 流程 -->
    <ol class="steps">
      <li v-for="(item, index) in steps" :key="item.title" class="step" :class="stepState(index)">
        <span class="step-index">
          <el-icon v-if="stepState(index) === 'done'" :size="13"><Check /></el-icon>
          <template v-else>{{ index + 1 }}</template>
        </span>
        <div class="step-body">
          <div class="step-title">{{ item.title }}</div>
          <div class="step-desc">{{ item.desc }}</div>
        </div>
      </li>
    </ol>

    <section class="card card-pad">
      <div class="section-title">
        <h3>{{ meta.uploaded ? '当前简历' : '上传简历' }}</h3>
        <span class="muted">PDF / DOC / DOCX · 不超过 {{ maxMb }}MB</span>
      </div>

      <div v-if="meta.uploaded" class="file-card">
        <div class="file-icon" :class="'tone-' + fileTone">{{ fileExt }}</div>
        <div class="file-main">
          <div class="file-name">{{ meta.fileName }}</div>
          <div class="file-meta">
            {{ formatSize(meta.sizeBytes) }} · 上传于 {{ fmtTime(meta.uploadedAt) }}
          </div>
        </div>
        <div class="file-actions">
          <el-button size="small" @click="download">下载</el-button>
          <el-button size="small" :loading="uploading" @click="pick">重新上传</el-button>
          <el-button size="small" text type="danger" @click="remove">删除</el-button>
        </div>
      </div>

      <div
        v-else
        class="dropzone"
        :class="{ dragging }"
        role="button"
        tabindex="0"
        @click="pick"
        @keydown.enter="pick"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="drop-icon">
          <el-icon :size="24"><UploadFilled /></el-icon>
        </div>
        <div class="drop-title">把简历拖到这里，或点击选择文件</div>
        <div class="drop-sub">支持 PDF / DOC / DOCX，单个文件不超过 {{ maxMb }}MB</div>
      </div>

      <input ref="inputRef" type="file" accept=".pdf,.doc,.docx" hidden @change="onPick" />

      <div v-if="uploading" class="upload-progress">
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: percent + '%' }" />
        </div>
        <span class="tiny">{{ percent }}%</span>
      </div>

      <div v-if="error" class="form-alert">
        <el-icon class="form-alert-icon"><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <p class="upload-note">
        面试开始后仍可更新，导师看到的始终是最新版本。文件仅本人与报考你的导师可见，不对外公开。
      </p>

      <!-- 简历解析结果：技术栈 + 经历 + 性格特质，导师端 AI 初评用的是同一份画像 -->
      <div v-if="meta.uploaded" class="tag-block">
        <div class="tag-head">
          <span class="tag-title">简历解析结果</span>
          <span class="tiny">
            {{ (meta.tags || []).length }} 项技能 · 正文 {{ meta.textChars || 0 }} 字
          </span>
        </div>

        <template v-if="(meta.tags || []).length">
          <div class="sub-title">技能 / 技术栈</div>
          <div class="tag-cloud">
            <span v-for="tag in meta.tags" :key="tag.name" class="tag-pill" :title="tag.category">{{ tag.name }}</span>
          </div>
        </template>

        <template v-if="experiences.length">
          <div class="sub-title">项目与实践经历</div>
          <ul class="exp-list">
            <li v-for="(item, index) in experiences" :key="index">{{ item }}</li>
          </ul>
        </template>

        <template v-if="traits.length">
          <div class="sub-title">性格与特质</div>
          <div class="trait-cloud">
            <span v-for="trait in traits" :key="trait" class="trait-pill">{{ trait }}</span>
          </div>
        </template>

        <p v-if="!meta.tags?.length && !experiences.length && !traits.length" class="tag-empty">
          暂未从简历正文识别到有效信息，可在简历里写清用过的语言、框架、做过的项目与个人特点。
        </p>
      </div>
    </section>

    <section class="card card-pad">
      <div class="section-title">
        <h3>简历建议</h3>
        <span class="muted">面试官通常只翻 1 分钟</span>
      </div>
      <div class="tip-grid">
        <div v-for="item in tips" :key="item.title" class="tip" :class="'tone-' + item.tone">
          <div class="tip-title">{{ item.title }}</div>
          <div class="tip-desc">{{ item.detail }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/index.js'
import { fmtTime } from '../../utils/labels.js'
import { formatSize, saveBlob } from '../../utils/download.js'

const maxMb = 10
const steps = [
  { title: '准备文件', desc: '一页 PDF 最稳，项目写到能被追问' },
  { title: '上传校验', desc: '自动检查格式与大小，上传即替换旧版' },
  { title: '导师查看', desc: '面试前导师可下载，AI 初评会参考' }
]

const tips = [
  { title: '项目写结果', detail: '每个项目一句「做了什么 + 结果」，比堆技术名词有效。', tone: 'blue' },
  { title: '技能别写满', detail: '只写能被追问 3 层的技能，其余放到项目里体现。', tone: 'teal' },
  { title: '一页为限', detail: '导师平均看 1 分钟，页码越多越容易被跳过。', tone: 'purple' },
  { title: '命名规范', detail: '建议「姓名-专业-方向.pdf」，下载后一眼能认。', tone: 'orange' }
]

const meta = reactive({ uploaded: false, fileName: '', sizeBytes: null, uploadedAt: null })
const experiences = computed(() => meta.profile?.experiences || [])
const traits = computed(() => meta.profile?.traits || [])
const loading = ref(false)
const uploading = ref(false)
const percent = ref(0)
const error = ref('')
const dragging = ref(false)
const inputRef = ref(null)

const fileExt = computed(() => {
  const name = String(meta.fileName || '')
  const dot = name.lastIndexOf('.')
  return dot < 0 ? 'FILE' : name.slice(dot + 1).toUpperCase()
})

const fileTone = computed(() => (fileExt.value === 'PDF' ? 'orange' : 'blue'))

function stepState(index) {
  if (meta.uploaded) return 'done'
  if (uploading.value && index === 1) return 'current'
  if (index === 0) return 'done'
  return index === 1 ? 'current' : 'todo'
}

async function load() {
  loading.value = true
  try {
    const data = await api.studentResume()
    Object.assign(meta, { uploaded: false, fileName: '', sizeBytes: null, uploadedAt: null }, data || {})
  } finally {
    loading.value = false
  }
}

function pick() {
  if (uploading.value) return
  inputRef.value?.click()
}

function onPick(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) upload(file)
}

function onDrop(event) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) upload(file)
}

function validate(file) {
  const name = String(file.name || '')
  const ext = name.slice(name.lastIndexOf('.') + 1).toLowerCase()
  if (!['pdf', 'doc', 'docx'].includes(ext)) {
    return '仅支持 PDF / DOC / DOCX 格式'
  }
  if (file.size > maxMb * 1024 * 1024) {
    return `文件超过 ${maxMb}MB，请压缩后再上传`
  }
  return ''
}

async function upload(file) {
  error.value = validate(file)
  if (error.value) return
  uploading.value = true
  percent.value = 0
  try {
    const data = await api.uploadResume(file, (event) => {
      if (event.total) percent.value = Math.min(99, Math.round((event.loaded / event.total) * 100))
    })
    Object.assign(meta, { uploaded: false, fileName: '', sizeBytes: null, uploadedAt: null }, data || {})
    percent.value = 100
    ElMessage.success('简历已上传')
  } catch (err) {
    error.value = err.message || '上传失败，请重试'
  } finally {
    uploading.value = false
    setTimeout(() => {
      percent.value = 0
    }, 400)
  }
}

async function download() {
  try {
    const blob = await api.studentResumeBlob()
    saveBlob(blob, meta.fileName || '简历.pdf')
  } catch (err) {
    error.value = err.message || '下载失败，请稍后重试'
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm('删除后导师将看不到这份简历，确认删除？', '删除简历', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await api.deleteResume()
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 简历技能标签 */
.tag-block {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--separator);
}

.tag-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 9px;
}

.tag-title {
  font-size: 12.5px;
  font-weight: 650;
  color: var(--label-2);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sub-title {
  margin: 14px 0 7px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--label-3);
  letter-spacing: 0.02em;
}

.exp-list {
  margin: 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.exp-list li {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--label-1);
}

.trait-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.trait-pill {
  font-size: 11.5px;
  line-height: 1.5;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(52, 199, 89, 0.12);
  color: var(--ios-green-dark);
  white-space: nowrap;
}

.tag-pill {
  font-size: 11.5px;
  line-height: 1.5;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(0, 122, 255, 0.1);
  color: var(--ios-blue);
  white-space: nowrap;
}

.tag-empty {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--label-3);
}

/* 流程条 */
.steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.step {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-xs);
}

.step-index {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--label-3);
  background: var(--fill-1);
}

.step.done .step-index {
  color: #fff;
  background: var(--ios-green);
}

.step.current .step-index {
  color: #fff;
  background: var(--ios-blue);
  box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.12);
}

.step-title {
  font-size: 13px;
  font-weight: 600;
}

.step-desc {
  margin-top: 2px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--label-3);
}

/* 文件卡片 */
.file-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--fill-2);
}

.file-icon {
  width: 46px;
  height: 46px;
  flex: none;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--tone-deep, var(--ios-blue-dark));
  background: var(--tone-soft, rgba(0, 122, 255, 0.12));
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.file-meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--label-3);
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 拖拽上传 */
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 34px 20px;
  border: 1.5px dashed var(--separator-strong);
  border-radius: 16px;
  background: var(--surface-2);
  cursor: pointer;
  text-align: center;
  transition: border-color 0.22s var(--ease-ios), background-color 0.22s var(--ease-ios),
    transform 0.22s var(--ease-ios);
}

.dropzone:hover,
.dropzone.dragging {
  border-color: var(--ios-blue);
  background: rgba(0, 122, 255, 0.05);
}

.dropzone.dragging {
  transform: scale(1.005);
}

.drop-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ios-blue);
  background: rgba(0, 122, 255, 0.1);
}

.drop-title {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
}

.drop-sub {
  font-size: 12px;
  color: var(--label-3);
}

/* 进度与错误 */
.upload-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.progress-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--fill-1);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #007aff, #5ac8fa);
  transition: width 0.2s linear;
}

.form-alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 11px;
  border: 1px solid rgba(255, 59, 48, 0.32);
  background: rgba(255, 59, 48, 0.08);
  color: var(--ios-red-dark);
  font-size: 12.5px;
  line-height: 1.6;
}

.form-alert-icon {
  flex: none;
  font-size: 15px;
  margin-top: 1px;
}

.upload-note {
  margin: 14px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--label-3);}

/* 建议 */
.tip-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.tip {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--fill-2);
  border-left: 3px solid var(--tone, var(--ios-blue));
}

.tip-title {
  font-size: 13px;
  font-weight: 600;
}

.tip-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--label-2);
}

@media (max-width: 900px) {
  .steps {
    grid-template-columns: 1fr;
  }

  .file-card {
    flex-wrap: wrap;
  }

  .file-main {
    flex: 1 1 60%;
  }

  .file-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
