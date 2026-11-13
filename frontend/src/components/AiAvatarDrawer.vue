<template>
  <teleport to="body">
    <div class="avatar-layer" :class="{ show: modelValue }" :aria-hidden="!modelValue">
      <div class="avatar-scrim" @click="close" />

      <section class="avatar-sheet" role="dialog" aria-label="导师 AI 分身">
        <header class="sheet-head">
          <div class="ai-mark" :class="{ human: mode === 'direct' }">{{ mode === 'ai' ? 'AI' : '直' }}</div>
          <div class="who">
            <div class="who-name">{{ mode === 'ai' ? displayName : `${tutor?.tutorName || '导师'}老师` }}</div>
            <div class="who-sub">
              {{ tutor?.labName }} · {{ mode === 'ai' ? tutor?.researchDirection : '直接留言，老师登录后可见并回复' }}
            </div>
          </div>
          <div class="mode-switch" role="tablist" aria-label="沟通方式">
            <button type="button" role="tab" :aria-selected="mode === 'ai'" :class="{ active: mode === 'ai' }" @click="switchMode('ai')">
              AI 分身
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'direct'"
              :class="{ active: mode === 'direct' }"
              @click="switchMode('direct')"
            >
              直接留言
            </button>
          </div>
          <button class="sheet-close" type="button" aria-label="关闭" @click="close">
            <el-icon :size="17"><Close /></el-icon>
          </button>
        </header>

        <div ref="scroller" class="bubbles">
          <div v-for="(message, index) in activeMessages" :key="index" class="bubble" :class="message.mine ? 'student' : 'assistant'">
            <div class="bubble-text">{{ message.content }}</div>
            <div v-if="message.citations && message.citations.length" class="bubble-cite">
              依据：{{ message.citations.join('；') }}
            </div>
            <div v-if="message.time" class="bubble-time">{{ message.time }}</div>
          </div>
          <div v-if="sending || directLoading" class="bubble assistant">
            <span class="ai-dots"><i /><i /><i /></span>
          </div>
        </div>

        <div v-if="mode === 'ai'" class="quick">
          <button v-for="item in quickQuestions" :key="item" type="button" @click="ask(item)">
            {{ item }}
          </button>
        </div>

        <div v-if="errorMsg" class="drawer-error">
          <el-icon class="drawer-error-icon"><WarningFilled /></el-icon>
          <span>{{ errorMsg }}</span>
        </div>

        <footer class="composer">
          <input
            v-model="draft"
            class="composer-input"
            type="text"
            :placeholder="mode === 'ai' ? '问问方向、项目要求、面试准备…' : '直接给老师留言，回车发送…'"
            @keyup.enter="send()"
          />
          <button class="composer-send" type="button" :disabled="!draft.trim() || sending || directSending" @click="send()">
            发送
          </button>
        </footer>

        <p class="sheet-note">
          {{ mode === 'ai' ? boundary : '这里是你和老师的私聊，消息仅双方可见；老师不在线时留言即可。' }}
          <span v-if="mode === 'ai' && placeholder" class="placeholder-tag">规则引擎预览</span>
        </p>
      </section>
    </div>
  </teleport>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { api } from '../api/index.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  tutor: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const boundary = ref('不承诺录取结果、不评价其他导师、不透露面试题目')
const placeholder = ref(false)
const errorMsg = ref('')
const scroller = ref(null)

// 当前会话归属的导师：切换导师时据此判断要不要丢弃旧上下文
const sessionTutorId = ref(null)
// 会话代次：切换导师后旧请求才返回时直接丢弃，避免答案串到新导师的界面
let chatSeq = 0

const DEFAULT_BOUNDARY = '不承诺录取结果、不评价其他导师、不透露面试题目'

// 师生直连：模式 ai=AI 分身（秒回） / direct=真实私信（老师回复）
const mode = ref('ai')
const directMessages = ref([])
const directLoading = ref(false)
const directSending = ref(false)

const quickQuestions = ['方向主要做什么？', '需要提前准备什么？', '面试会考察哪些方面？', '还有名额吗？']

const displayName = computed(() => props.tutor?.displayName || `${props.tutor?.tutorName || '导师'}老师助手`)

const activeMessages = computed(() =>
  mode.value === 'ai'
    ? messages.value.map((m) => ({
        mine: m.role === 'student',
        content: m.content,
        citations: m.citations,
        time: ''
      }))
    : directMessages.value.map((m) => ({
        mine: Number(m.mine) === 1,
        content: m.content,
        citations: [],
        time: m.createdAt ? String(m.createdAt).slice(5, 16) : ''
      }))
)

/** 清空当前会话的全部上下文（消息、草稿、错误、代次） */
function resetConversation() {
  chatSeq += 1
  messages.value = []
  directMessages.value = []
  draft.value = ''
  errorMsg.value = ''
  placeholder.value = false
  boundary.value = DEFAULT_BOUNDARY
  sending.value = false
  directSending.value = false
  directLoading.value = false
}

/** 确保当前展示的是当前导师的会话；导师变了就先清空再初始化 */
async function ensureConversation() {
  const id = props.tutor?.tutorId
  if (sessionTutorId.value !== id) {
    resetConversation()
    sessionTutorId.value = id
  }
  if (mode.value === 'direct') {
    await loadDirect()
    return
  }
  if (!messages.value.length) {
    messages.value = [
      {
        role: 'assistant',
        content:
          props.tutor?.greeting ||
          `你好，我是${props.tutor?.tutorName || '导师'}老师的 AI 分身，可以聊聊方向和面试准备。`,
        citations: []
      }
    ]
  }
  await nextTick()
  scrollToEnd()
}

watch(
  () => props.modelValue,
  async (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return
    await ensureConversation()
  }
)

// 抽屉开着时换到另一位导师，整个会话也要跟着换掉
watch(
  () => props.tutor?.tutorId,
  async () => {
    if (!props.modelValue) return
    await ensureConversation()
  }
)

function close() {
  emit('update:modelValue', false)
}

function scrollToEnd() {
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 切换沟通方式；进入直接留言时拉取真实会话。 */
async function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  errorMsg.value = ''
  if (next === 'direct') {
    await loadDirect()
  } else {
    await nextTick()
    scrollToEnd()
  }
}

async function loadDirect() {
  const userId = props.tutor?.userId
  if (!userId) {
    errorMsg.value = '缺少老师账号信息，无法建立会话'
    return
  }
  const seq = chatSeq
  directLoading.value = true
  errorMsg.value = ''
  try {
    const res = await api.messageConversation(userId)
    if (seq !== chatSeq) return
    directMessages.value = res?.messages || []
  } catch (error) {
    if (seq !== chatSeq) return
    errorMsg.value = `会话加载失败：${error.message}`
  } finally {
    if (seq === chatSeq) {
      directLoading.value = false
      await nextTick()
      scrollToEnd()
    }
  }
}

function ask(text) {
  draft.value = text
  send()
}

async function send() {
  if (mode.value === 'direct') {
    await sendDirect()
    return
  }
  const question = draft.value.trim()
  if (!question || sending.value || !props.tutor?.tutorId) return
  const seq = chatSeq
  messages.value.push({ role: 'student', content: question })
  draft.value = ''
  sending.value = true
  errorMsg.value = ''
  await nextTick()
  scrollToEnd()

  try {
    const res = await api.aiAvatarChat(props.tutor.tutorId, {
      question,
      history: messages.value.slice(-6).map((m) => ({ role: m.role, content: m.content }))
    })
    if (seq !== chatSeq) return
    const data = res?.data || {}
    placeholder.value = Boolean(res?.placeholder)
    if (data.boundary) boundary.value = data.boundary
    messages.value.push({
      role: 'assistant',
      content: data.answer || '这个问题我暂时回答不了，建议在开放日直接问老师。',
      citations: data.citations || []
    })
  } catch (error) {
    if (seq !== chatSeq) return
    errorMsg.value =
      error.code === 429
        ? `AI 额度不足，分身暂不可用：${error.message}`
        : `分身暂时不可用：${error.message}。可稍后重试或直接联系导师。`
  } finally {
    if (seq === chatSeq) {
      sending.value = false
      await nextTick()
      scrollToEnd()
    }
  }
}

/** 直接留言：走真实私信接口，消息落库，老师登录后可见并回复。 */
async function sendDirect() {
  const text = draft.value.trim()
  const userId = props.tutor?.userId
  if (!text || directSending.value || !userId) return
  const seq = chatSeq
  directSending.value = true
  errorMsg.value = ''
  try {
    const sent = await api.messageSend(userId, text)
    if (seq !== chatSeq) return
    directMessages.value.push(sent || { mine: 1, content: text, createdAt: '' })
    draft.value = ''
    await nextTick()
    scrollToEnd()
  } catch (error) {
    if (seq !== chatSeq) return
    errorMsg.value = `发送失败：${error.message}`
  } finally {
    if (seq === chatSeq) directSending.value = false
  }
}
</script>

<style scoped>
.avatar-layer {
  position: fixed;
  inset: 0;
  z-index: 2000;
  pointer-events: none;
}

.avatar-layer.show {
  pointer-events: auto;
}

.avatar-scrim {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.24);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  opacity: 0;
  transition: opacity 0.3s var(--ease-ios);
}

.avatar-layer.show .avatar-scrim {
  opacity: 1;
}

.avatar-sheet {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(420px, 100vw);
  display: flex;
  flex-direction: column;
  background: var(--surface);
  box-shadow: -18px 0 48px rgba(0, 0, 0, 0.16);
  transform: translateX(102%);
  transition: transform 0.42s var(--ease-ios);
}

.avatar-layer.show .avatar-sheet {
  transform: translateX(0);
}

.sheet-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--separator);
  padding-top: calc(16px + env(safe-area-inset-top));
}

.ai-mark {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(150deg, #5856d6, #af52de);
  box-shadow: 0 4px 12px rgba(88, 86, 214, 0.28);
}

.who {
  min-width: 0;
}

.who-name {
  font-size: 14px;
  font-weight: 650;
}

.who-sub {
  font-size: 11.5px;
  color: var(--label-3);
  margin-top: 1px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 240px;
}

.sheet-close {
  margin-left: auto;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--fill-1);
  color: var(--label-2);
  cursor: pointer;
}

.bubbles {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--bg-grouped);
}

.bubble {
  max-width: 86%;
  padding: 9px 12px;
  border-radius: 16px;
  font-size: 13.5px;
  line-height: 1.65;
  animation: bubble-in 0.26s var(--ease-out) both;
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.bubble.assistant {
  align-self: flex-start;
  background: var(--surface);
  color: var(--label-1);
  border-bottom-left-radius: 5px;
  box-shadow: var(--shadow-xs);
}

.bubble.student {
  align-self: flex-end;
  background: var(--ios-blue);
  color: #fff;
  border-bottom-right-radius: 5px;
}

.bubble-cite {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--label-3);
}

.bubble.student .bubble-cite {
  color: rgba(255, 255, 255, 0.75);
}

.quick {
  display: flex;
  gap: 6px;
  padding: 10px 16px 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.quick::-webkit-scrollbar {
  display: none;
}

.quick button {
  flex: none;
  font-family: var(--font-ios);
  font-size: 12px;
  padding: 5px 11px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--separator);
  background: var(--surface);
  color: var(--label-2);
  cursor: pointer;
  transition: background-color 0.18s var(--ease-ios), color 0.18s var(--ease-ios);
}

.quick button:hover {
  background: var(--fill-1);
  color: var(--label-1);
}

.drawer-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 4px 16px 8px;
  padding: 9px 12px;
  border-radius: 11px;
  border: 1px solid rgba(255, 59, 48, 0.32);
  background: rgba(255, 59, 48, 0.08);
  color: var(--ios-red-dark);
  font-size: 12px;
  line-height: 1.6;
}

.drawer-error-icon {
  flex: none;
  font-size: 14px;
  margin-top: 1px;
}

.composer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--separator);
}

.composer-input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--fill-1);
  font-family: var(--font-ios);
  font-size: 13.5px;
  color: var(--label-1);
  outline: none;
  transition: background-color 0.2s var(--ease-ios), box-shadow 0.2s var(--ease-ios);
}

.composer-input:focus {
  background: var(--surface);
  box-shadow: 0 0 0 2px var(--ios-blue) inset;
}

.composer-send {
  height: 38px;
  padding: 0 16px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--ios-blue);
  color: #fff;
  font-family: var(--font-ios);
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s var(--ease-ios), transform 0.16s var(--ease-ios);
}

.composer-send:disabled {
  background: var(--fill-3);
  color: var(--label-3);
  cursor: not-allowed;
}

.composer-send:not(:disabled):active {
  transform: scale(0.97);
}

.sheet-note {
  margin: 0;
  padding: 0 16px calc(12px + env(safe-area-inset-bottom));
  font-size: 11px;
  line-height: 1.6;
  color: var(--label-3);
}

.placeholder-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: rgba(255, 149, 0, 0.14);
  color: var(--ios-orange-dark);
}

@media (max-width: 560px) {
  .avatar-sheet {
    width: 100%;
  }
}

/* ---- 沟通方式切换（AI 分身 / 直接留言）---- */
.mode-switch {
  display: flex;
  flex: none;
  padding: 2px;
  border-radius: var(--radius-pill);
  background: var(--fill-1);
  margin-left: 2px;
}

.mode-switch button {
  border: none;
  background: transparent;
  padding: 5px 10px;
  border-radius: var(--radius-pill);
  font-family: var(--font-ios);
  font-size: 11px;
  color: var(--label-2);
  cursor: pointer;
  transition: background-color 0.2s var(--ease-ios), color 0.2s var(--ease-ios);
  white-space: nowrap;
}

.mode-switch button.active {
  background: var(--surface);
  color: #4b49b8;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.ai-mark.human {
  background: linear-gradient(150deg, #34c759, #30b0c7);
  box-shadow: 0 4px 12px rgba(52, 199, 89, 0.26);
}

.bubble-time {
  margin-top: 4px;
  font-size: 10px;
  color: var(--label-3);
  font-variant-numeric: tabular-nums;
}

.bubble.student .bubble-time {
  text-align: right;
}
</style>
