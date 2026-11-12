<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>私信</h2>
        <p>
          学生可直接联系任意导师/负责人，导师登录后回复。消息仅双方可见，
          <span v-if="totalUnread">当前 <strong>{{ totalUnread }}</strong> 条未读。</span>
          <span v-else>暂无未读。</span>
        </p>
      </div>
    </div>

    <div class="chat-layout">
      <!-- 会话列表 -->
      <aside class="thread-pane">
        <div v-if="!threads.length" class="empty">还没有会话</div>
        <button
          v-for="item in threads"
          :key="item.userId"
          type="button"
          class="thread-item"
          :class="{ active: peer && peer.userId === item.userId }"
          @click="openThread(item)"
        >
          <div class="avatar">{{ (item.userName || '?').slice(0, 1) }}</div>
          <div class="thread-main">
            <div class="thread-top">
              <span class="thread-name">{{ item.userName }}</span>
              <span class="tiny">{{ shortTime(item.lastAt) }}</span>
            </div>
            <div class="thread-sub">
              <span class="role-tag">{{ roleLabel(item.userRole) }}</span>
              <span v-if="item.labName" class="tiny">{{ item.labName }}</span>
            </div>
            <div class="thread-last">{{ item.lastContent }}</div>
          </div>
          <span v-if="Number(item.unreadCount) > 0" class="unread">{{ item.unreadCount }}</span>
        </button>
      </aside>

      <!-- 对话区 -->
      <section class="chat-pane">
        <div v-if="!peer" class="empty chat-empty">从左侧选择一个会话开始沟通</div>

        <template v-else>
          <header class="chat-head">
            <div class="avatar">{{ peer.userName.slice(0, 1) }}</div>
            <div>
              <div class="chat-name">{{ peer.userName }}</div>
              <div class="tiny">{{ roleLabel(peer.role) }} · {{ peer.labName || '—' }}</div>
            </div>
          </header>

          <div ref="scroller" class="bubbles">
            <div v-for="message in messages" :key="message.id" class="bubble" :class="Number(message.mine) === 1 ? 'mine' : 'theirs'">
              <div class="bubble-text">{{ message.content }}</div>
              <div class="bubble-time">{{ message.createdAt }}</div>
            </div>
            <div v-if="loading" class="bubble theirs"><span class="ai-dots"><i /><i /><i /></span></div>
          </div>

          <div v-if="errorMsg" class="chat-error">{{ errorMsg }}</div>

          <footer class="composer">
            <input
              v-model="draft"
              class="composer-input"
              type="text"
              placeholder="输入消息，回车发送…"
              @keyup.enter="send()"
            />
            <button class="composer-send" type="button" :disabled="!draft.trim() || sending" @click="send()">发送</button>
          </footer>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { api } from '../../api/index.js'

const threads = ref([])
const peer = ref(null)
const messages = ref([])
const draft = ref('')
const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const scroller = ref(null)

const totalUnread = computed(() =>
  threads.value.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0)
)

function roleLabel(role) {
  return role === 'STUDENT' ? '学生' : role === 'LAB_ADMIN' ? '实训室负责人' : role === 'TUTOR' ? '讲师' : '管理员'
}

function shortTime(value) {
  return value ? String(value).slice(5, 16) : ''
}

async function loadThreads() {
  try {
    const res = await api.messageThreads()
    threads.value = res?.threads || []
  } catch (error) {
    errorMsg.value = error.message
  }
}

async function openThread(item) {
  peer.value = { userId: item.userId, userName: item.userName, role: item.userRole, labName: item.labName }
  await loadConversation()
}

async function loadConversation() {
  if (!peer.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await api.messageConversation(peer.value.userId)
    messages.value = res?.messages || []
    await nextTick()
    scrollToEnd()
    await loadThreads()
  } catch (error) {
    errorMsg.value = error.message
  } finally {
    loading.value = false
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value || !peer.value) return
  sending.value = true
  errorMsg.value = ''
  try {
    const sent = await api.messageSend(peer.value.userId, text)
    messages.value.push(sent || { id: Date.now(), mine: 1, content: text, createdAt: '' })
    draft.value = ''
    await nextTick()
    scrollToEnd()
    await loadThreads()
  } catch (error) {
    errorMsg.value = error.message
  } finally {
    sending.value = false
  }
}

function scrollToEnd() {
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

onMounted(loadThreads)
</script>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 14px;
  min-height: 480px;
  height: calc(100vh - 220px);
}

.thread-pane {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  padding: 8px;
}

.thread-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s var(--ease-ios);
}

.thread-item:hover {
  background: var(--fill-1);
}

.thread-item.active {
  background: rgba(88, 86, 214, 0.1);
}

.avatar {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(88, 86, 214, 0.12);
  color: #4b49b8;
  font-size: 14px;
  font-weight: 600;
}

.thread-main {
  flex: 1;
  min-width: 0;
}

.thread-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.thread-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--label-1);
}

.thread-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.role-tag {
  font-size: 10.5px;
  color: var(--label-2);
  background: var(--fill-1);
  border-radius: 5px;
  padding: 1px 6px;
}

.thread-last {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--label-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unread {
  flex: none;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--radius-pill);
  background: #ff3b30;
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-pane {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--separator);
}

.chat-name {
  font-size: 14px;
  font-weight: 600;
}

.bubbles {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--surface-2);
}

.bubble {
  max-width: 72%;
  align-self: flex-start;
}

.bubble.mine {
  align-self: flex-end;
}

.bubble-text {
  padding: 9px 12px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.65;
  background: var(--surface);
  border: 1px solid var(--separator);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble.mine .bubble-text {
  background: rgba(88, 86, 214, 0.92);
  border-color: transparent;
  color: #fff;
}

.bubble-time {
  margin-top: 3px;
  font-size: 10px;
  color: var(--label-3);
  font-variant-numeric: tabular-nums;
}

.bubble.mine .bubble-time {
  text-align: right;
}

.chat-error {
  padding: 8px 16px;
  font-size: 12px;
  color: #b3261e;
  background: rgba(255, 59, 48, 0.08);
}

.composer {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--separator);
}

.composer-input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--surface-2);
  font-family: var(--font-ios);
  font-size: 13px;
  outline: none;
}

.composer-input:focus {
  border-color: rgba(88, 86, 214, 0.5);
}

.composer-send {
  flex: none;
  padding: 0 18px;
  height: 38px;
  border: none;
  border-radius: var(--radius-pill);
  background: #5856d6;
  color: #fff;
  font-family: var(--font-ios);
  font-size: 13px;
  cursor: pointer;
}

.composer-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--label-3);
  font-size: 13px;
}

.chat-empty {
  margin: auto;
}

.tiny {
  font-size: 11px;
  color: var(--label-3);
}

@media (max-width: 900px) {
  .chat-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .thread-pane {
    max-height: 220px;
  }

  .chat-pane {
    min-height: 420px;
  }
}
</style>
