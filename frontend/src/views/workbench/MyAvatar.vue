<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的 AI 分身</h2>
        <p>学生在「导师与方向」里问的就是这个分身。这里能看到它接的是哪个模型，也能改它的口吻与知识范围。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <!-- 接入状态：先回答「到底接没接上」，再谈设定 -->
    <section class="status-card" :class="{ offline: !meta.connected }">
      <div class="status-main">
        <div class="status-badge">
          <i />
          {{ meta.connected ? '已接入真实模型' : '未接入，走规则引擎' }}
        </div>
        <div class="status-name">{{ meta.persona?.displayName || '加载中…' }}</div>
        <div class="status-sub">
          {{ meta.labName }} · {{ meta.researchDirection }}
        </div>
      </div>
      <dl class="status-facts">
        <div>
          <dt>通道</dt>
          <dd>{{ meta.provider || '—' }}</dd>
        </div>
        <div>
          <dt>模型</dt>
          <dd>{{ meta.model || '—' }}</dd>
        </div>
        <div>
          <dt>身份来源</dt>
          <dd>{{ meta.persona?.source === 'custom' ? '自定义' : '按导师资料推导' }}</dd>
        </div>
      </dl>
    </section>

    <div class="grid">
      <!-- 设定 -->
      <section class="card card-pad">
        <div class="section-title">
          <h3>分身设定</h3>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>

        <el-form label-position="top" class="avatar-form">
          <el-form-item label="分身名称">
            <el-input v-model="form.displayName" placeholder="如：王艳华老师的 AI 分身" />
          </el-form-item>
          <el-form-item label="说话语气">
            <el-input v-model="form.tone" placeholder="如：严谨、耐心，像实验室的师兄师姐" />
          </el-form-item>
          <el-form-item label="开场白">
            <el-input v-model="form.greeting" type="textarea" :rows="2" placeholder="学生打开对话时看到的第一句话" />
          </el-form-item>
          <el-form-item label="知识范围">
            <el-input
              v-model="form.knowledge"
              type="textarea"
              :rows="6"
              placeholder="研究方向、项目要求、组会安排、希望学生具备的基础……写进来的内容分身的回答才会用"
            />
          </el-form-item>
          <el-form-item label="回答边界">
            <el-input v-model="form.boundaries" type="textarea" :rows="2" placeholder="如：不承诺录取结果、不评价其他导师" />
          </el-form-item>
        </el-form>

        <p class="form-note">
          分身只依据这里的知识与导师档案回答，不会编造名额，也不会替你做录取承诺。
        </p>
      </section>

      <!-- 试聊 -->
      <section class="card card-pad chat-card">
        <div class="section-title">
          <h3>试聊</h3>
          <span class="tiny">与学生提问走同一条链路</span>
        </div>

        <div ref="scroller" class="chat-body">
          <div v-for="(item, index) in messages" :key="index" class="msg" :class="item.mine ? 'mine' : 'assistant'">
            <div class="msg-text">{{ item.content }}</div>
            <div v-if="item.citations && item.citations.length" class="msg-cite">
              依据：{{ item.citations.join('；') }}
            </div>
          </div>
          <div v-if="sending" class="msg assistant">
            <span class="ai-dots"><i /><i /><i /></span>
            <span v-if="waitSeconds >= 4" class="msg-wait">已等待 {{ waitSeconds }}s</span>
          </div>
          <div v-if="errorMsg" class="chat-error">{{ errorMsg }}</div>
        </div>

        <div class="chat-composer">
          <input
            v-model="draft"
            class="chat-input"
            type="text"
            placeholder="以学生口吻问一句，比如：方向主要做什么？"
            @keyup.enter="ask"
          />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="ask">发送</el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'

const loading = ref(false)
const saving = ref(false)
const meta = ref({})

const form = reactive({
  displayName: '',
  tone: '',
  greeting: '',
  knowledge: '',
  boundaries: ''
})

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const errorMsg = ref('')
const scroller = ref(null)
const waitSeconds = ref(0)
let waitTimer = null

function fillForm(persona) {
  form.displayName = persona?.displayName || ''
  form.tone = persona?.tone || ''
  form.greeting = persona?.greeting || ''
  form.knowledge = persona?.knowledge || ''
  form.boundaries = persona?.boundaries || ''
}

async function load() {
  loading.value = true
  try {
    const data = await api.myAvatar()
    meta.value = data || {}
    fillForm(data?.persona)
    if (!messages.value.length) {
      messages.value = [
        {
          mine: false,
          content: data?.persona?.greeting || '你好，我是你的 AI 分身。',
          citations: []
        }
      ]
    }
  } catch (error) {
    ElMessage.error(error.message || '分身状态加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await api.saveMyAvatar({ ...form })
    ElMessage.success('已保存，学生端立即生效')
    await load()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function startClock() {
  waitSeconds.value = 0
  clearInterval(waitTimer)
  waitTimer = setInterval(() => {
    waitSeconds.value += 1
  }, 1000)
}

function stopClock() {
  clearInterval(waitTimer)
  waitTimer = null
}

async function ask() {
  const question = draft.value.trim()
  if (!question || sending.value) return
  messages.value.push({ mine: true, content: question, citations: [] })
  draft.value = ''
  errorMsg.value = ''
  sending.value = true
  startClock()
  try {
    const res = await api.myAvatarChat(question)
    const data = res?.data || {}
    messages.value.push({
      mine: false,
      content: data.answer || '这次没有返回内容，可再问一次。',
      citations: data.citations || []
    })
  } catch (error) {
    errorMsg.value = error.timeout
      ? '响应超时：首次唤醒或模型繁忙时偶发，再问一次通常即可。'
      : `分身暂时不可用：${error.message}`
  } finally {
    stopClock()
    sending.value = false
  }
}

onMounted(load)
onUnmounted(stopClock)
</script>

<style scoped>
.status-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  padding: 22px 26px;
  border-radius: 24px;
  background:
    radial-gradient(120% 110% at 86% -30%, rgba(255, 255, 255, 0.96), transparent 54%),
    linear-gradient(130deg, #dff7ef 0%, #c6ece1 46%, #f7ecd7 100%);
  border: 1px solid rgba(255, 255, 255, 0.96);
  box-shadow:
    0 26px 50px -34px rgba(20, 78, 70, 0.95),
    0 2px 6px rgba(20, 78, 70, 0.04),
    inset 0 1px 0 #fff;
}

.status-card.offline {
  background:
    radial-gradient(120% 120% at 88% -30%, rgba(255, 255, 255, 0.95), transparent 58%),
    linear-gradient(132deg, #fdf7ec 0%, #f8ebd5 52%, #eef4f1 100%);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  color: #fff;
  background: var(--brand-500);
  box-shadow: 0 10px 20px -14px rgba(47, 157, 146, 1);
}

.status-card.offline .status-badge {
  background: var(--sand-500);
  box-shadow: 0 10px 20px -14px rgba(217, 164, 95, 1);
}

.status-badge i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
}

.status-name {
  margin: 10px 0 3px;
  font-size: 21px;
  font-weight: 780;
  letter-spacing: -0.03em;
  color: var(--ink-900);
}

.status-sub {
  font-size: 12.5px;
  color: var(--ink-600);
}

.status-facts {
  display: flex;
  gap: 26px;
  margin: 0;
}

.status-facts dt {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--ink-400);
}

.status-facts dd {
  margin: 3px 0 0;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.avatar-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.avatar-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-600);
}

.form-note {
  margin: 4px 0 0;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-500);
  background: var(--surface-sunken);
}

/* ---- 试聊 ---- */

.chat-card {
  display: flex;
  flex-direction: column;
  min-height: 480px;
}

.chat-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 2px 12px;
  overflow-y: auto;
  max-height: 420px;
}

.msg {
  max-width: 88%;
  padding: 10px 13px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.68;
  animation: msg-in 0.26s var(--clay-ease) both;
}

@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.msg.assistant {
  align-self: flex-start;
  background: var(--surface-sunken);
  color: var(--ink-800);
  border-bottom-left-radius: 5px;
}

.msg.mine {
  align-self: flex-end;
  background: var(--brand-500);
  color: #fff;
  border-bottom-right-radius: 5px;
}

.msg-cite {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--ink-400);
}

.msg.mine .msg-cite {
  color: rgba(255, 255, 255, 0.78);
}

.msg-wait {
  margin-left: 8px;
  font-size: 11.5px;
  color: var(--ink-400);
  font-variant-numeric: tabular-nums;
}

.chat-error {
  padding: 9px 12px;
  border-radius: 11px;
  border: 1px solid rgba(210, 106, 97, 0.3);
  background: rgba(210, 106, 97, 0.08);
  color: #b3524a;
  font-size: 12px;
  line-height: 1.6;
}

.chat-composer {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(28, 111, 102, 0.07);
}

.chat-input {
  flex: 1;
  height: 40px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--surface-sunken);
  font-family: var(--font-ios);
  font-size: 13.5px;
  color: var(--ink-900);
  outline: none;
  transition: background-color 0.2s var(--clay-ease), border-color 0.2s var(--clay-ease);
}

.chat-input:focus {
  background: var(--surface);
  border-color: var(--brand-300);
}

@media (max-width: 1000px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .status-facts {
    gap: 18px;
  }

  .chat-card {
    min-height: 380px;
  }
}
</style>
