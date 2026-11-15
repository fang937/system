<template>
  <div class="app">
    <div class="scrim" :class="{ show: drawer }" @click="drawer = false" />

    <aside class="sidebar" :class="{ open: drawer }">
      <div class="brand">
        <div class="brand-mark">双</div>
        <div class="brand-text">
          <div class="brand-name">实训室双选</div>
          <div class="brand-sub">河北师范大学</div>
        </div>
        <button class="brand-close" type="button" aria-label="关闭菜单" @click="drawer = false">
          <el-icon :size="17"><Close /></el-icon>
        </button>
      </div>

      <nav class="nav">
        <div v-for="group in visibleMenus" :key="group.group" class="nav-group" :class="'tone-' + group.tone">
          <div class="nav-title">{{ group.group }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: route.path === item.path }"
          >
            <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
            <span class="nav-label">{{ item.label }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-foot">
        <div class="me">
          <span class="me-avatar">{{ (store.user?.name || '?').slice(0, 1) }}</span>
          <div class="me-text">
            <div class="me-name">{{ store.user?.name || store.user?.username }}</div>
            <div class="me-role">
              {{ store.user?.roleLabel }}
              <span v-if="store.user?.labName"> · {{ store.user.labName }}</span>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <div class="shell">
      <header class="topbar">
        <button class="icon-btn menu-btn" type="button" aria-label="打开菜单" @click="drawer = true">
          <el-icon :size="19"><Menu /></el-icon>
        </button>

        <div class="phase-badge" :class="phaseState">
          <span class="pb-dot" />
          <div class="pb-text">
            <span class="pb-label">{{ phaseState === 'active' ? '正在进行' : '流程状态' }}</span>
            <span v-if="store.currentPhase" class="pb-name">{{ store.currentPhase.name }}</span>
            <span v-else class="pb-name">当前无进行中阶段</span>
          </div>
          <span v-if="store.currentPhase" class="pb-count">{{ countdownText }}</span>
        </div>

        <div class="spacer" />

        <el-popover placement="bottom-end" :width="360" trigger="click" popper-class="notice-pop">
          <template #reference>
            <el-badge :value="unread" :hidden="unread === 0" class="bell">
              <button class="icon-btn" type="button" aria-label="消息通知">
                <el-icon :size="18"><Bell /></el-icon>
              </button>
            </el-badge>
          </template>
          <div class="notify-head">
            <span>消息通知</span>
            <el-button link type="primary" size="small" :disabled="unread === 0" @click="readAll">
              全部已读
            </el-button>
          </div>
          <div v-if="!notices.length" class="notify-empty">暂无通知</div>
          <div class="notify-list">
            <div v-for="n in notices" :key="n.id" class="notify-item" :class="{ unread: !n.isRead }">
              <div class="notify-title">{{ n.title }}</div>
              <div class="notify-body">{{ n.content }}</div>
              <div class="notify-time">{{ n.createdAt }}</div>
            </div>
          </div>
        </el-popover>

        <el-dropdown @command="onCommand">
          <span class="user">
            <span class="user-avatar">{{ (store.user?.name || '?').slice(0, 1) }}</span>
            <span class="user-name">{{ store.user?.name || store.user?.username }}</span>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>账号：{{ store.user?.username }}</el-dropdown-item>
              <el-dropdown-item disabled>身份：{{ store.user?.roleLabel }}</el-dropdown-item>
              <el-dropdown-item command="notifications" divided>消息通知</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="main">
        <div class="main-inner">
          <router-view v-slot="{ Component }">
            <transition name="view" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { useUserStore } from '../stores/user.js'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const MENUS = [
  {
    group: '学生端',
    cap: 'STUDENT',
    tone: 'blue',
    items: [
      { path: '/student/tutors', label: '导师与方向', icon: 'User' },
      { path: '/student/application', label: '我的志愿', icon: 'EditPen' },
      { path: '/student/resume', label: '我的简历', icon: 'Document' },
      { path: '/student/offers', label: '我的 Offer', icon: 'Tickets' },
      { path: '/student/result', label: '录取结果', icon: 'Medal' },
      { path: '/messages', label: '私信', icon: 'ChatDotRound' }
    ]
  },
  {
    group: 'AI 助手',
    cap: 'STUDENT',
    tone: 'indigo',
    items: [
      { path: '/student/resume-optimize', label: 'AI 优化简历', icon: 'MagicStick' },
      { path: '/student/interview-coach', label: 'AI 面试辅导', icon: 'Microphone' }
    ]
  },
  {
    group: '导师事务',
    cap: 'TUTOR_WORK',
    tone: 'indigo',
    items: [
      { path: '/workbench/applicants', label: '报考我的学生', icon: 'UserFilled' },
      { path: '/workbench/offers', label: '我的录取', icon: 'DocumentChecked' },
      { path: '/workbench/avatar', label: '我的 AI 分身', icon: 'MagicStick' },
      { path: '/messages', label: '学生留言', icon: 'ChatDotRound' }
    ]
  },
  {
    group: '实训室事务',
    cap: 'LAB_MANAGE',
    tone: 'teal',
    items: [
      { path: '/workbench/progress', label: '本室招生进度', icon: 'DataAnalysis' },
      { path: '/workbench/schedules', label: '面试排期', icon: 'Calendar' },
      { path: '/workbench/lab-adjust', label: '室内调剂', icon: 'Switch' }
    ]
  },
  {
    group: '学院管理',
    cap: 'ADMIN',
    tone: 'purple',
    items: [
      { path: '/admin/dashboard', label: '全局看板', icon: 'Odometer' },
      { path: '/admin/phases', label: '阶段与轮次', icon: 'SetUp' },
      { path: '/admin/tutors', label: '导师与实训室', icon: 'OfficeBuilding' },
      { path: '/admin/adjust', label: '统筹调剂', icon: 'Sort' },
      { path: '/admin/report', label: '结果公示', icon: 'Files' }
    ]
  }
]

const visibleMenus = computed(() =>
  MENUS.filter((group) => store.caps.includes(group.cap)).map((group) => ({
    group: group.group,
    tone: group.tone,
    items: group.items
  }))
)

const notices = ref([])
const unread = computed(() => notices.value.filter((n) => !n.isRead).length)
const now = ref(Date.now())
let timer = null
let poller = null

/* 移动端抽屉 */
const drawer = ref(false)
watch(
  () => route.path,
  () => {
    drawer.value = false
  }
)
watch(drawer, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

const phaseState = computed(() => {
  const phase = store.currentPhase
  if (!phase) return 'none'
  if (phase.state === 'PENDING') return 'pending'
  if (phase.state === 'CLOSED') return 'closed'
  return 'active'
})

const countdownText = computed(() => {
  const phase = store.currentPhase
  if (!phase) return ''
  const end = new Date(String(phase.endAt).replace(/-/g, '/')).getTime()
  const diff = end - now.value
  if (diff <= 0) return '本阶段已结束'
  const d = Math.floor(diff / 86400000)
  const h = Math.floor((diff % 86400000) / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  return `剩余 ${d > 0 ? d + ' 天 ' : ''}${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

async function loadNotices() {
  try {
    const data = await api.notifications()
    notices.value = data?.list || []
  } catch {
    /* 通知加载失败不影响主流程 */
  }
}

async function readAll() {
  await api.readAllNotifications()
  await loadNotices()
}

function onCommand(command) {
  if (command === 'logout') {
    store.logout()
    router.replace('/login')
  }
  if (command === 'notifications') {
    router.push('/notifications')
  }
}

onMounted(async () => {
  if (!store.user) {
    await store.fetchMe().catch(() => {})
  }
  await store.refreshPhase()
  loadNotices()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  poller = setInterval(() => {
    store.refreshPhase()
    loadNotices()
  }, 60000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  clearInterval(poller)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.app {
  position: relative;
  display: flex;
  min-height: 100vh;
  background: var(--bg-grouped);
}

/* 背景柔光：四种色相很淡地铺底，让毛玻璃有层次 */
.app::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(880px 520px at 92% -12%, rgba(0, 122, 255, 0.1), transparent 62%),
    radial-gradient(620px 420px at 4% -8%, rgba(48, 176, 199, 0.1), transparent 64%),
    radial-gradient(700px 460px at -6% 104%, rgba(175, 82, 222, 0.09), transparent 64%),
    radial-gradient(560px 380px at 78% 108%, rgba(255, 149, 0, 0.06), transparent 62%);
}

/* ---------------- 侧边栏 ---------------- */

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 40;
  width: 276px;
  display: flex;
  flex-direction: column;
  padding: 16px 14px 12px;
  background: var(--glass);
  backdrop-filter: saturate(180%) blur(28px);
  -webkit-backdrop-filter: saturate(180%) blur(28px);
  border-right: 1px solid var(--separator);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 4px 8px 14px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 11px;
  background: linear-gradient(150deg, #0a84ff, #5856d6 58%, #af52de);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(88, 86, 214, 0.26), inset 0 1px 0 rgba(255, 255, 255, 0.34);
}

.brand-name {
  font-size: 14.5px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--label-1);
}

.brand-sub {
  margin-top: 1px;
  font-size: 11.5px;
  color: var(--label-3);
}

.brand-close {
  display: none;
  margin-left: auto;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--fill-1);
  color: var(--label-2);
  cursor: pointer;
}

.nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 2px;
}

.nav-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 10px 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--label-3);
}

.nav-title::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tone, var(--label-3));
  opacity: 0.9;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 11px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 500;
  color: var(--label-2);
  transition: background-color 0.2s var(--ease-ios), color 0.2s var(--ease-ios),
    box-shadow 0.26s var(--ease-ios), transform 0.16s var(--ease-ios);
}

.nav-item + .nav-item {
  margin-top: 2px;
}

.nav-item:hover {
  background: rgba(120, 120, 128, 0.09);
  color: var(--label-1);
}

.nav-item:active {
  transform: scale(0.985);
}

.nav-item.active {
  background: var(--surface);
  color: var(--tone-deep, var(--ios-blue));
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05), 0 8px 20px rgba(0, 0, 0, 0.06);
}

.nav-item.active .nav-icon {
  color: var(--tone, var(--ios-blue));
}

.nav-icon {
  font-size: 17px;
  opacity: 0.92;
}

.nav-label {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sidebar-foot {
  padding-top: 10px;
  border-top: 1px solid var(--separator);
  padding-bottom: env(safe-area-inset-bottom);
}

.me {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 14px;
}

.me-avatar {
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--ios-blue-dark);
  background: linear-gradient(160deg, #eaf3ff, #dbe9ff);
}

.me-text {
  min-width: 0;
}

.me-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--label-1);
}

.me-role {
  margin-top: 1px;
  font-size: 11.5px;
  color: var(--label-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------------- 主体 ---------------- */

.shell {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  margin-left: 276px;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 24px;
  background: var(--glass);
  backdrop-filter: saturate(180%) blur(24px);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
  border-bottom: 1px solid var(--separator);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--label-2);
  cursor: pointer;
  transition: background-color 0.2s var(--ease-ios), color 0.2s var(--ease-ios),
    transform 0.16s var(--ease-ios);
}

.icon-btn:hover {
  background: var(--fill-1);
  color: var(--label-1);
}

.icon-btn:active {
  transform: scale(0.94);
}

.menu-btn {
  display: none;
}

/* 顶栏「当前阶段」标识：进行中时为实色渐变，确保一眼看到 */
.phase-badge {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 12px;
  border-radius: 12px;
  background: var(--fill-1);
  color: var(--label-2);
  max-width: 46vw;
  transition: background 0.3s var(--ease-ios), box-shadow 0.3s var(--ease-ios);
}

.phase-badge .pb-dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: currentColor;
}

.phase-badge .pb-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.18;
}

.phase-badge .pb-label {
  font-size: 9.5px;
  opacity: 0.72;
  letter-spacing: 0.08em;
}

.phase-badge .pb-name {
  font-size: 12.5px;
  font-weight: 650;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.phase-badge .pb-count {
  flex: none;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.06);
}

.phase-badge.active {
  background: linear-gradient(135deg, #22b455, #159fb5);
  color: #fff;
  box-shadow: 0 4px 14px rgba(34, 180, 85, 0.32);
}

.phase-badge.active .pb-dot {
  background: #fff;
  animation: dot-pulse 1.8s var(--ease-ios) infinite;
}

.phase-badge.active .pb-count {
  background: rgba(255, 255, 255, 0.22);
}

.phase-badge.pending {
  background: rgba(255, 149, 0, 0.14);
  color: var(--ios-orange-dark);
}

.phase-badge.closed {
  background: var(--fill-1);
  color: var(--label-3);
}

@keyframes dot-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.55);
  }
  55% {
    box-shadow: 0 0 0 6px rgba(255, 255, 255, 0);
  }
}

.spacer {
  flex: 1;
}

.bell {
  margin-right: 2px;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px 0 4px;
  border-radius: var(--radius-pill);
  cursor: pointer;
  font-size: 13px;
  color: var(--label-1);
  transition: background-color 0.2s var(--ease-ios);
}

.user:hover {
  background: var(--fill-1);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(160deg, #0a84ff, #0062cc);
  box-shadow: 0 2px 8px rgba(0, 122, 255, 0.24);
}

.user-caret {
  font-size: 12px;
  color: var(--label-3);
}

.main {
  flex: 1;
  padding: 24px 28px 44px;
}

.main-inner {
  max-width: 1280px;
  margin: 0 auto;
}

/* ---------------- 通知气泡 ---------------- */

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  font-weight: 650;
  margin-bottom: 4px;
}

.notify-list {
  max-height: 380px;
  overflow-y: auto;
  margin: 0 -4px;
}

.notify-item {
  padding: 10px 4px;
  border-top: 1px solid var(--separator);
}

.notify-item:first-child {
  border-top: none;
}

.notify-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--label-3);
}

.notify-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--label-1);
}

.notify-item.unread .notify-title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ios-blue);
  margin-right: 6px;
  vertical-align: middle;
}

.notify-body {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--label-2);
}

.notify-time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--label-3);
}

/* ---------------- 遮罩与过渡 ---------------- */

.scrim {
  position: fixed;
  inset: 0;
  z-index: 35;
  background: rgba(0, 0, 0, 0.24);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.3s var(--ease-ios), visibility 0.3s var(--ease-ios);
}

.scrim.show {
  opacity: 1;
  visibility: visible;
}

.view-enter-active {
  transition: opacity 0.26s var(--ease-out), transform 0.26s var(--ease-out);
}

.view-leave-active {
  transition: opacity 0.14s ease;
}

.view-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.view-leave-to {
  opacity: 0;
}

/* ---------------- 响应式 ---------------- */

@media (min-width: 901px) {
  .scrim {
    display: none;
  }
}

@media (max-width: 900px) {
  .sidebar {
    width: 288px;
    padding-top: calc(14px + env(safe-area-inset-top));
    border-right: none;
    box-shadow: var(--shadow-lg);
    transform: translateX(-102%);
    transition: transform 0.42s var(--ease-ios);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .brand-close {
    display: inline-flex;
  }

  .shell {
    margin-left: 0;
  }

  .topbar {
    height: calc(56px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 12px 0;
    gap: 8px;
  }

  .menu-btn {
    display: inline-flex;
  }

  .countdown {
    display: none;
  }

  .phase-pill {
    max-width: 52vw;
    font-size: 12px;
  }

  .user-name {
    display: none;
  }

  .user {
    padding: 0 4px;
  }

  .main {
    padding: 16px 14px calc(36px + env(safe-area-inset-bottom));
  }
}

@media (max-width: 400px) {
  .phase-pill {
    max-width: 46vw;
  }
}

/* ============================================================================
   薄荷黏土主题覆盖
   —— 放在本块最后，同权重下覆盖上方基础样式；不改动结构与交互
   ============================================================================ */

.app {
  background: transparent;
}

/* 画布渐变交给 body 统一铺，避免两层叠出脏色 */
.app::before {
  display: none;
}

/* ---------------- 侧边栏 ---------------- */

.sidebar {
  width: 252px;
  padding: 20px 14px 14px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(150%) blur(26px);
  -webkit-backdrop-filter: saturate(150%) blur(26px);
  border-right: 1px solid rgba(28, 111, 102, 0.08);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.9);
}

.brand {
  padding: 2px 8px 18px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 13px;
  font-size: 17px;
  background: linear-gradient(150deg, #63c9bb, #2f9d92 62%, #237b72);
  box-shadow: 0 10px 20px -10px rgba(47, 157, 146, 0.9), inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--ink-900);
}

.brand-sub {
  margin-top: 2px;
  font-size: 11px;
  letter-spacing: 0.02em;
  color: var(--ink-400);
}

.nav {
  padding: 0 2px;
}

.nav-title {
  padding: 16px 12px 7px;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--ink-400);
}

.nav-title::before {
  width: 5px;
  height: 5px;
  opacity: 1;
  background: var(--tone, var(--brand-400));
}

.nav-item {
  height: 44px;
  gap: 11px;
  padding: 0 12px;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-600);
}

.nav-item + .nav-item {
  margin-top: 3px;
}

.nav-item:hover {
  background: rgba(47, 157, 146, 0.06);
  color: var(--ink-900);
}

.nav-item.active {
  background: linear-gradient(135deg, #e9f9f4 0%, #d3f0e8 100%);
  color: var(--brand-700);
  font-weight: 650;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 8px 18px -14px rgba(28, 111, 102, 0.9);
}

.nav-item.active .nav-icon {
  color: var(--tone, var(--brand-600));
}

.nav-icon {
  font-size: 17px;
  opacity: 1;
}

/* ---------------- 侧栏底部账号 ---------------- */

.sidebar-foot {
  padding-top: 12px;
  border-top: 1px solid rgba(28, 111, 102, 0.07);
}

.me {
  gap: 10px;
  padding: 9px 10px;
  border-radius: 16px;
  background: rgba(47, 157, 146, 0.055);
}

.me-avatar {
  width: 34px;
  height: 34px;
  font-size: 13px;
  font-weight: 700;
  color: var(--brand-700);
  background: linear-gradient(155deg, #ffffff, #d8f1ea);
  box-shadow: inset 0 1px 0 #fff, 0 4px 10px -6px rgba(28, 111, 102, 0.6);
}

.me-name {
  font-size: 13px;
  font-weight: 650;
  color: var(--ink-900);
}

.me-role {
  font-size: 11px;
  color: var(--ink-400);
}

/* ---------------- 顶栏 ---------------- */

.shell {
  margin-left: 252px;
}

.topbar {
  height: 70px;
  gap: 12px;
  padding: 0 28px;
  background: rgba(247, 251, 249, 0.66);
  backdrop-filter: saturate(160%) blur(20px);
  -webkit-backdrop-filter: saturate(160%) blur(20px);
  border-bottom: 1px solid rgba(28, 111, 102, 0.06);
}

.icon-btn {
  color: var(--ink-500);
}

.icon-btn:hover {
  background: rgba(47, 157, 146, 0.08);
  color: var(--brand-700);
}

/* 当前阶段：胶囊徽章，进行中为薄荷实底 */
.phase-badge {
  gap: 10px;
  height: 40px;
  padding: 0 15px 0 13px;
  border-radius: 999px;
  background: var(--cream-100);
  color: var(--ink-600);
  border: 1px solid rgba(217, 164, 95, 0.22);
  max-width: 48vw;
}

.phase-badge .pb-dot {
  width: 7px;
  height: 7px;
}

.phase-badge .pb-text {
  gap: 0;
  line-height: 1.15;
}

.phase-badge .pb-label {
  display: block;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.09em;
  opacity: 0.62;
}

.phase-badge .pb-name {
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.phase-badge .pb-count {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(28, 111, 102, 0.07);
}

.phase-badge.active {
  background: linear-gradient(120deg, #3cb8a8 0%, #2f9d92 55%, #248a80 100%);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 10px 24px -14px rgba(47, 157, 146, 1), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.phase-badge.active .pb-label {
  opacity: 0.78;
}

.phase-badge.active .pb-count {
  background: rgba(255, 255, 255, 0.22);
}

.phase-badge.pending {
  background: var(--cream-100);
  color: var(--sand-600);
  border-color: rgba(217, 164, 95, 0.3);
}

.phase-badge.closed {
  background: rgba(91, 125, 119, 0.08);
  color: var(--ink-400);
  border-color: transparent;
}

/* ---------------- 用户区 ---------------- */

.user {
  height: 40px;
  gap: 8px;
  padding: 0 12px 0 5px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-800);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: inset 0 1px 0 #fff, 0 6px 16px -14px rgba(28, 111, 102, 0.9);
}

.user:hover {
  background: #fff;
}

.user-avatar {
  width: 30px;
  height: 30px;
  font-size: 12px;
  font-weight: 700;
  background: linear-gradient(155deg, #63c9bb, #2f9d92);
  box-shadow: 0 4px 10px -6px rgba(47, 157, 146, 1);
}

.user-caret {
  color: var(--ink-400);
}

/* ---------------- 主区 ---------------- */

.main {
  padding: 26px 30px 52px;
}

.main-inner {
  max-width: 1320px;
}

/* ---------------- 通知面板 ---------------- */

.notify-head {
  color: var(--ink-900);
}

.notify-item {
  border-top-color: rgba(28, 111, 102, 0.07);
}

.notify-title {
  color: var(--ink-900);
}

.notify-body {
  color: var(--ink-500);
}

.notify-item.unread .notify-title::before {
  background: var(--brand-500);
}

/* ---------------- 响应式 ---------------- */

@media (max-width: 900px) {
  .sidebar {
    width: 280px;
    background: rgba(255, 255, 255, 0.96);
    box-shadow: var(--clay-shadow-lg);
  }

  .shell {
    margin-left: 0;
  }

  .topbar {
    height: calc(60px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 14px 0;
  }

  .main {
    padding: 16px 14px calc(40px + env(safe-area-inset-bottom));
  }

  .phase-badge {
    max-width: 54vw;
    height: 36px;
  }

  .phase-badge .pb-count {
    display: none;
  }
}
</style>
