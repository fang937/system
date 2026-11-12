<template>
  <teleport to="body">
    <div class="avatar-layer" :class="{ show: modelValue }" :aria-hidden="!modelValue">
      <div class="avatar-scrim" @click="close" />

      <section class="avatar-sheet" role="dialog" aria-label="导师 AI 分身">
        <header class="sheet-head">
          <div class="ai-mark">AI</div>
          <div class="who">
            <div class="who-name">{{ displayName }}</div>
            <div class="who-sub">{{ tutor?.labName }} · {{ tutor?.researchDirection }}</div>
          </div>
          <button class="sheet-close" type="button" aria-label="关闭" @click="close">
            <el-icon :size="17"><Close /></el-icon>
          </button>
        </header>

        <div ref="scroller" class="bubbles">
          <div v-for="(message, index) in messages" :key="index" class="bubble" :class="message.role">
            <div class="bubble-text">{{ message.content }}</div>
            <div v-if="message.citations && message.citations.length" class="bubble-cite">
              依据：{{ message.citations.join('；') }}
            </div>
          </div>
          <div v-if="sending" class="bubble assistant">
            <span class="ai-dots"><i /><i /><i /></span>
          </div>
        </div>

        <div class="quick">
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
            placeholder="问问方向、项目要求、面试准备…"
            @keyup.enter="send()"
          />
          <button class="composer-send" type="button" :disabled="!draft.trim() || sending" @click="send()">
            发送
          </button>
        </footer>

        <p class="sheet-note">
          {{ boundary }}
          <span v-if="placeholder" class="placeholder-tag">规则引擎预览</span>
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

const quickQuestions = ['方向主要做什么？', '需要提前准备什么？', '面试会考察哪些方面？', '还有名额吗？']

const displayName = computed(() => props.tutor?.displayName || `${props.tutor?.tutorName || '导师'}老师助手`)

watch(
  () => props.modelValue,
  async (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return
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
)

function close() {
  emit('update:modelValue', false)
}

function scrollToEnd() {
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

function ask(text) {
  draft.value = text
  send()
}

async function send() {
  const question = draft.value.trim()
  if (!question || sending.value || !props.tutor?.tutorId) return
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
    const data = res?.data || {}
    placeholder.value = Boolean(res?.placeholder)
    if (data.boundary) boundary.value = data.boundary
    messages.value.push({
      role: 'assistant',
      content: data.answer || '这个问题我暂时回答不了，建议在开放日直接问老师。',
      citations: data.citations || []
    })
  } catch (error) {
    errorMsg.value =
      error.code === 429
        ? `AI 额度不足，分身暂不可用：${error.message}`
        : `分身暂时不可用：${error.message}。可稍后重试或直接联系导师。`
  } finally {
    sending.value = false
    await nextTick()
    scrollToEnd()
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
</style>
