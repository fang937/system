<template>
  <div class="login" :class="{ 'is-immersive': uiHidden }">
    <div class="bg-video" aria-hidden="true">
      <video
        ref="videoA"
        class="bg-video__el"
        :class="{ 'is-visible': visibleSlot === 0 }"
        muted
        playsinline
        preload="auto"
        @ended="onEnded(0)"
        @playing="onPlaying(0)"
        @error="onVideoError"
      />
      <video
        ref="videoB"
        class="bg-video__el"
        :class="{ 'is-visible': visibleSlot === 1 }"
        muted
        playsinline
        preload="auto"
        @ended="onEnded(1)"
        @playing="onPlaying(1)"
        @error="onVideoError"
      />
      <div class="bg-video__veil" />
    </div>

    <div class="scene" aria-hidden="true">
      <span class="scene-blob sb-a" />
      <span class="scene-blob sb-b" />
      <span class="scene-blob sb-c" />
      <span class="scene-grid" />
    </div>

    <div class="login-card" :inert="uiHidden" :aria-hidden="uiHidden">
      <section class="brand-panel">
        <div class="brand-top">
          <div class="logo">双</div>
          <div>
            <div class="brand-name">学生实训室双向选择系统</div>
            <div class="brand-sub">河北师范大学 · 实训室选组选拔</div>
          </div>
        </div>

        <div class="brand-main">
          <span class="eyebrow">{{ currentPortal.title }}</span>
          <h1>{{ currentPortal.title }}登录</h1>
          <p>{{ currentPortal.desc }}</p>
        </div>

        <div class="art" aria-hidden="true">
          <span class="art-ribbon" />
          <span class="art-book bk-1" />
          <span class="art-book bk-2" />
          <span class="art-book bk-3" />
          <span class="art-orb orb-a" />
          <span class="art-orb orb-b" />
          <span class="art-orb orb-c" />
          <span class="art-card" />
        </div>

        <div class="flow">
          <div v-for="(step, index) in steps" :key="step" class="flow-step">
            <span class="flow-index">{{ index + 1 }}</span>
            <span>{{ step }}</span>
          </div>
        </div>
      </section>

      <section class="form-panel">
        <div class="form-card">
          <div class="portals" role="tablist" aria-label="登录入口">
            <button
              v-for="item in portals"
              :key="item.key"
              type="button"
              role="tab"
              :aria-selected="portal === item.key"
              class="portal"
              :class="{ active: portal === item.key }"
              @click="switchPortal(item.key)"
            >
              <el-icon class="portal-icon"><component :is="item.icon" /></el-icon>
              <span class="portal-title">{{ item.title }}</span>
            </button>
          </div>

          <div class="form-head">
            <h2>{{ currentPortal.title }}登录</h2>
            <p>{{ currentPortal.hint }}</p>
          </div>

          <el-form :model="form" label-position="top" class="login-form" @submit.prevent>
            <el-form-item label="账号">
              <el-input
                v-model="form.username"
                size="large"
                :placeholder="currentPortal.placeholder"
                autocomplete="username"
              />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="form.password"
                size="large"
                type="password"
                show-password
                placeholder="登录密码"
                autocomplete="current-password"
                @keyup.enter="submit"
              />
            </el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
              进入{{ currentPortal.title }}
            </el-button>
          </el-form>

          <div class="demo">
            <div class="demo-head">
              <span>演示账号 · 点击填入</span>
              <span class="demo-hint">密码均为 123456</span>
            </div>
            <div class="demo-list">
              <button
                v-for="account in demoAccounts[portal]"
                :key="account.username"
                type="button"
                class="demo-item"
                @click="fill(account)"
              >
                <span class="demo-role">{{ account.role }}</span>
                <span class="demo-account">{{ account.username }}</span>
                <span class="demo-name">{{ account.name }}</span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div class="footnote" :aria-hidden="uiHidden">
      入口与身份强绑定：学生端仅限学生账号；导师端仅限导师与实训室负责人；管理员端仅限系统管理员。
      实训室负责人身份由管理员在「导师与实训室」中授予。
    </div>

    <button
      type="button"
      class="ui-toggle"
      :title="uiHidden ? '显示登录界面（Esc）' : '隐藏登录界面，只看校园风光'"
      :aria-label="uiHidden ? '显示登录界面' : '隐藏登录界面'"
      @click="toggleUi"
    >
      <el-icon><component :is="uiHidden ? 'View' : 'Hide'" /></el-icon>
      <span>{{ uiHidden ? '显示登录' : '隐藏登录' }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user.js'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

/* ---------------- 校园风光背景视频（按顺序循环播放） ---------------- */

const campusVideos = [
  '/media/login/campus-01-beimen.mp4',
  '/media/login/campus-02-museum.mp4',
  '/media/login/campus-03-shiguangta.mp4',
  '/media/login/campus-04-library.mp4',
  '/media/login/campus-05-xiyuehu.mp4',
  '/media/login/campus-06-zhenzhi.mp4'
]

const videoA = ref(null)
const videoB = ref(null)
const visibleSlot = ref(-1)
const playingIndex = ref(0)
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false
const mobileQuery =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(max-width: 560px)')
    : null

let switchTimer = null

const videoOf = (slot) => (slot === 0 ? videoA.value : videoB.value)

/** 先把下一段静默加载到另一个 video 槽位，切换时无需再等缓冲。 */
function loadSlot(slot, index) {
  const el = videoOf(slot)
  if (!el) return
  el.src = campusVideos[index]
  el.load()
}

async function playSlot(slot, index) {
  const el = videoOf(slot)
  if (!el) return
  const url = campusVideos[index]
  if (el.getAttribute('src') !== url) {
    el.src = url
    el.load()
  }
  el.muted = true
  try {
    if (el.readyState >= 1) {
      el.currentTime = 0
    }
    await el.play()
    playingIndex.value = index
    loadSlot(slot === 0 ? 1 : 0, (index + 1) % campusVideos.length)
  } catch {
    // 浏览器自动播放策略拦截时，等用户首次交互再试
    armResumeOnInteraction()
  }
}

function onPlaying(slot) {
  visibleSlot.value = slot
}

function onEnded(slot) {
  if (slot !== visibleSlot.value) return
  const nextSlot = slot === 0 ? 1 : 0
  const nextIndex = (playingIndex.value + 1) % campusVideos.length
  visibleSlot.value = -1
  clearTimeout(switchTimer)
  switchTimer = setTimeout(() => playSlot(nextSlot, nextIndex), 560)
}

function onVideoError() {
  // 片段加载失败就停在当前海报，不阻塞登录
  visibleSlot.value = -1
}

let resumeArmed = false
function armResumeOnInteraction() {
  if (resumeArmed) return
  resumeArmed = true
  const retry = () => {
    resumeArmed = false
    playSlot(visibleSlot.value === 1 ? 1 : 0, playingIndex.value)
  }
  window.addEventListener('pointerdown', retry, { once: true })
}

function onVisibilityChange() {
  const el = videoOf(visibleSlot.value === 1 ? 1 : 0)
  if (!el || reduceMotion) return
  if (document.hidden) {
    el.pause()
  } else {
    el.play().catch(() => {})
  }
}

function syncForViewport() {
  if (mobileQuery?.matches) {
    videoA.value?.pause()
    videoB.value?.pause()
  } else if (!document.hidden && !reduceMotion) {
    playSlot(visibleSlot.value === 1 ? 1 : 0, playingIndex.value)
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('keydown', onKeydown)
  mobileQuery?.addEventListener('change', syncForViewport)
  if (reduceMotion || mobileQuery?.matches) return
  switchTimer = setTimeout(() => playSlot(0, 0), 180)
})

onBeforeUnmount(() => {
  clearTimeout(switchTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('keydown', onKeydown)
  mobileQuery?.removeEventListener('change', syncForViewport)
  document.body.style.overflow = ''
  videoA.value?.pause()
  videoB.value?.pause()
})

const loading = ref(false)
const form = reactive({ username: '', password: '' })
const uiHidden = ref(false)

function toggleUi() {
  uiHidden.value = !uiHidden.value
}

// 沉浸模式锁定页面滚动，恢复时还原
watch(uiHidden, (hidden) => {
  document.body.style.overflow = hidden ? 'hidden' : ''
})

function onKeydown(event) {
  if (event.key === 'Escape' && uiHidden.value) {
    uiHidden.value = false
  }
}

const portals = [
  {
    key: 'STUDENT',
    title: '学生端',
    icon: 'Reading',
    desc: '填报平行志愿、上传简历、确认 Offer、查看录取结果，全程可见自己处在流程的哪一步。',
    hint: '三个入口，各归其位；账号属于哪一端，就从哪一端进',
    placeholder: '学号'
  },
  {
    key: 'TUTOR',
    title: '导师端',
    icon: 'UserFilled',
    desc: '查看报考学生、面试打分与拟录取；实训室负责人还可编排面试、处理室内调剂。',
    hint: '导师与实训室负责人共用此入口，菜单按权限展开',
    placeholder: '工号'
  },
  {
    key: 'ADMIN',
    title: '管理员端',
    icon: 'OfficeBuilding',
    desc: '控制阶段与轮次、维护导师与实训室、全局看板与统筹调剂、结果公示与导出。',
    hint: '时间窗口是唯一权威，所有写操作按阶段放行',
    placeholder: '管理员账号'
  }
]

const portal = ref('STUDENT')
const currentPortal = computed(() => portals.find((item) => item.key === portal.value) || portals[0])

const demoAccounts = {
  STUDENT: [
    { role: '学生（空志愿）', username: '2023001', name: '林晓' },
    { role: '学生（已填志愿）', username: '2023002', name: '陈子昂' },
    { role: '学生（已面试待处置）', username: '2023903', name: '顾辰' },
    { role: '学生（2 个 Offer）', username: '2023904', name: '沈雨薇' },
    { role: '学生（3 个 Offer）', username: '2023913', name: '秦书瑶' },
    { role: '学生（已录取）', username: '2023907', name: '韩清和' }
  ],
  TUTOR: [
    { role: '202 负责人', username: 'liuguanjun', name: '刘冠军' },
    { role: '202 讲师', username: 'wangyanhua', name: '王艳华' },
    { role: '202 讲师', username: 'changlingling', name: '常玲玲' },
    { role: '202 讲师', username: 'fanjiahao', name: '范佳浩' },
    { role: '204 负责人', username: 'huguoxin', name: '胡国欣' },
    { role: '204 讲师', username: 'wangwei', name: '王伟' },
    { role: '204 讲师', username: 'lihuanzhen', name: '李焕贞' },
    { role: '402 负责人', username: 'jiahaitao', name: '贾海涛' },
    { role: '402 讲师', username: 'pingyuxuan', name: '平宇轩' },
    { role: '402 讲师', username: 'zhangxiang', name: '张翔' },
    { role: '402 讲师', username: 'wangxiaolong', name: '王晓龙' },
    { role: '404 负责人', username: 'gaoxuan', name: '高璇' },
    { role: '404 讲师', username: 'wangpengfan', name: '王鹏帆' },
    { role: '404 讲师', username: 'zhanghuibin', name: '张会斌' },
    { role: '404 讲师', username: 'wangfengpei', name: '王凤佩' },
    { role: '404 讲师', username: 'xuruize', name: '徐瑞泽' },
    { role: '406 负责人', username: 'yangzhenying', name: '杨振樱' },
    { role: '406 讲师', username: 'daiyifan', name: '代一凡' },
    { role: '406 讲师', username: 'maqingyu', name: '马庆宇' },
    { role: '406 讲师', username: 'pangkaiwen', name: '庞凯文' },
    { role: '406 讲师', username: 'dingleilei', name: '丁蕾蕾' },
    { role: '505 负责人', username: 'wanghao', name: '王浩' },
    { role: '505 讲师', username: 'liujialiang', name: '刘嘉亮' },
    { role: '505 讲师', username: 'liutianmeng', name: '刘天萌' },
    { role: '505 讲师', username: 'lina', name: '李娜' }
  ],
  ADMIN: [{ role: '系统管理员', username: 'admin', name: '灭建国' }]
}

const steps = ['宣讲与开放日', '意向填报', '面试与初选', '拟录取确认', '分轮补录', '统筹调剂', '结果公示']

function switchPortal(key) {
  if (portal.value === key) return
  portal.value = key
  form.username = ''
  form.password = ''
}

function fill(account) {
  form.username = account.username
  form.password = '123456'
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await store.login(form.username.trim(), form.password, portal.value)
    const redirect = route.query.redirect
    await router.replace(redirect && redirect !== '/' ? String(redirect) : store.homePath)
    ElMessage.success(`欢迎，${store.user?.name}`)
  } catch {
    /* 越界登录的提示由后端返回，拦截器统一弹出 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 28px 20px;
  overflow: hidden;
  background: linear-gradient(165deg, #f2f9f6 0%, #eaf4f1 42%, #f7f2e8 100%);
}

/* ---------------- 校园风光背景视频 ---------------- */

.bg-video {
  position: fixed;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  pointer-events: none;
  background: #e9f3f0 url('/media/login/campus-poster.jpg') center / cover no-repeat;
}

.bg-video__el {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.55s var(--clay-ease);
}

.bg-video__el.is-visible {
  opacity: 1;
}

.bg-video__veil {
  position: absolute;
  inset: 0;
  transition: opacity 0.5s var(--clay-ease);
  background:
    linear-gradient(
      180deg,
      rgba(247, 252, 250, 0.68) 0%,
      rgba(247, 252, 250, 0.36) 38%,
      rgba(244, 250, 247, 0.62) 100%
    ),
    linear-gradient(115deg, rgba(47, 157, 146, 0.12), rgba(255, 255, 255, 0) 46%, rgba(226, 189, 131, 0.12));
}

.is-immersive .bg-video__veil {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .bg-video__el {
    display: none;
  }
}

/* 手机端整屏都是表单卡片，视频既看不见也白耗流量，直接不显示 */
@media (max-width: 560px) {
  .bg-video {
    display: none;
  }
}

/* ---------------- 背景场景 ---------------- */

.scene {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.scene-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
}

.sb-a {
  width: 620px;
  height: 620px;
  top: -240px;
  right: -160px;
  background: rgba(127, 211, 199, 0.42);
}

.sb-b {
  width: 520px;
  height: 520px;
  bottom: -260px;
  left: -180px;
  background: rgba(242, 221, 184, 0.5);
}

.sb-c {
  width: 420px;
  height: 420px;
  top: 34%;
  left: 46%;
  background: rgba(174, 228, 218, 0.3);
}

.scene-grid {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  background-image: radial-gradient(rgba(28, 111, 102, 0.09) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(circle at 50% 46%, #000 0%, transparent 72%);
  -webkit-mask-image: radial-gradient(circle at 50% 46%, #000 0%, transparent 72%);
}

/* ---------------- 主卡片 ---------------- */

.login-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1040px;
  display: grid;
  grid-template-columns: 1.04fr 1fr;
  background: var(--surface);
  border-radius: 34px;
  box-shadow:
    0 30px 70px -40px rgba(20, 78, 70, 0.45),
    0 2px 6px rgba(20, 78, 70, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;
  animation: login-in 0.62s var(--clay-ease);
  transition: opacity 0.42s var(--clay-ease), transform 0.42s var(--clay-ease);
}

.is-immersive .login-card {
  opacity: 0;
  transform: translateY(10px) scale(0.985);
  pointer-events: none;
}

@keyframes login-in {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.986);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------------- 左侧品牌 ---------------- */

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 34px 44px 30px;
  background:
    radial-gradient(120% 90% at 80% 8%, rgba(255, 255, 255, 0.85), transparent 58%),
    linear-gradient(160deg, #edfaf5 0%, #d9f3eb 48%, #f8f1e3 100%);
  overflow: hidden;
}

.brand-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 750;
  color: #fff;
  background: linear-gradient(150deg, #63c9bb, #2f9d92 62%, #237b72);
  box-shadow: 0 12px 22px -12px rgba(47, 157, 146, 1), inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.brand-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-500);
}

.brand-main {
  position: relative;
  z-index: 2;
  margin-top: 6px;
}

.eyebrow {
  display: inline-block;
  margin-bottom: 10px;
  padding: 3px 11px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--brand-700);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px rgba(47, 157, 146, 0.16);
}

.brand-main h1 {
  margin: 0 0 12px;
  font-size: 32px;
  line-height: 1.3;
  font-weight: 780;
  letter-spacing: -0.035em;
  color: var(--ink-900);
}

.brand-main p {
  margin: 0;
  max-width: 34ch;
  font-size: 13px;
  line-height: 1.85;
  color: var(--ink-600);
}

/* ---------------- 插画（纯 CSS，无外部图片） ---------------- */

.art {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 140px;
  margin: 4px -8px 0;
}

.art-book {
  position: absolute;
  left: 50%;
  border-radius: 11px;
  transform-origin: center;
  box-shadow:
    0 18px 30px -20px rgba(20, 78, 70, 0.8),
    inset 0 2px 0 rgba(255, 255, 255, 0.7),
    inset 0 -4px 0 rgba(20, 78, 70, 0.06);
}

.bk-1 {
  width: 168px;
  height: 32px;
  bottom: 92px;
  background: linear-gradient(180deg, #fcebcf, #eecb93);
  transform: translateX(-58%) rotate(-5deg);
}

.bk-2 {
  width: 190px;
  height: 34px;
  bottom: 60px;
  background: linear-gradient(180deg, #ffffff, #cdece4);
  transform: translateX(-46%) rotate(3deg);
}

.bk-3 {
  width: 152px;
  height: 28px;
  bottom: 34px;
  background: linear-gradient(180deg, #f8f2e6, #e2d5b9);
  transform: translateX(-54%) rotate(-2deg);
}

.art-ribbon {
  position: absolute;
  left: -22px;
  bottom: -30px;
  width: 286px;
  height: 186px;
  border: 9px solid rgba(240, 190, 130, 0.4);
  border-radius: 50%;
  border-right-color: transparent;
  border-bottom-color: transparent;
  transform: rotate(-14deg);
}

.art-card {
  position: absolute;
  right: 6px;
  top: 4px;
  width: 62px;
  height: 62px;
  border-radius: 18px;
  background: linear-gradient(150deg, #ffffff, #e2f5ef);
  box-shadow: 0 16px 26px -18px rgba(20, 78, 70, 0.9), inset 0 1px 0 #fff;
  transform: rotate(9deg);
}

.art-card::after {
  content: '';
  position: absolute;
  inset: 18px;
  border-radius: 8px;
  background: linear-gradient(150deg, #7fd3c7, #2f9d92);
  opacity: 0.85;
}

.art-orb {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, #ffffff, #a8e0d5 62%, #6cc6b8);
  box-shadow: 0 12px 20px -14px rgba(20, 78, 70, 0.9);
}

.orb-a {
  width: 22px;
  height: 22px;
  left: 8%;
  top: 22%;
}

.orb-b {
  width: 14px;
  height: 14px;
  right: 26%;
  bottom: 16%;
  background: radial-gradient(circle at 34% 30%, #fff, #f3dcb6 60%, #e2bd83);
}

.orb-c {
  width: 10px;
  height: 10px;
  right: 44%;
  top: 6%;
}

/* ---------------- 流程条 ---------------- */

.flow {
  position: relative;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: auto;
}

.flow-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px 5px 5px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ink-700);
  background: rgba(255, 255, 255, 0.66);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(6px);
}

.flow-index {
  width: 17px;
  height: 17px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9.5px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(150deg, #63c9bb, #2f9d92);
}

/* ---------------- 右侧表单 ---------------- */

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 46px;
  background: var(--surface);
}

.form-card {
  width: 100%;
  max-width: 372px;
}

/* 三端入口分段控件 */
.portals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 3px;
  padding: 4px;
  margin-bottom: 18px;
  background: var(--surface-sunken);
  border-radius: 16px;
}

.portal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 10px 4px 9px;
  border: none;
  border-radius: 13px;
  background: transparent;
  color: var(--ink-500);
  font-family: var(--font-ios);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.24s var(--clay-ease), color 0.24s var(--clay-ease),
    box-shadow 0.24s var(--clay-ease), transform 0.18s var(--clay-ease);
}

.portal:active {
  transform: scale(0.97);
}

.portal-icon {
  font-size: 17px;
  opacity: 0.8;
}

.portal.active {
  background: var(--surface);
  color: var(--brand-700);
  box-shadow: 0 2px 4px rgba(20, 78, 70, 0.06), 0 8px 18px -12px rgba(20, 78, 70, 0.6);
}

.portal.active .portal-icon {
  color: var(--brand-500);
  opacity: 1;
}

.form-head h2 {
  margin: 0 0 7px;
  font-size: 26px;
  font-weight: 780;
  letter-spacing: -0.032em;
  color: var(--ink-900);
}

.form-head p {
  margin: 0 0 16px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-500);
  min-height: 42px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.login-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-600);
}

.submit {
  width: 100%;
  height: 52px;
  margin-top: 6px;
  border-radius: 999px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
  background: linear-gradient(120deg, #3cb8a8, #2f9d92 60%, #248a80) !important;
  border: none !important;
  box-shadow: 0 16px 30px -18px rgba(47, 157, 146, 1), inset 0 1px 0 rgba(255, 255, 255, 0.28) !important;
}

.submit:hover {
  background: linear-gradient(120deg, #4bc4b4, #37a99d 60%, #2b968b) !important;
}

/* ---------------- 演示账号 ---------------- */

.demo {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(28, 111, 102, 0.08);
}

.demo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-500);
}

.demo-hint {
  font-weight: 500;
  color: var(--ink-400);
}

.demo-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 5px;
  border-radius: 16px;
  background: var(--surface-sunken);
  max-height: 224px;
  overflow-y: auto;
}

.demo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: 11px;
  background: var(--surface);
  box-shadow: 0 1px 2px rgba(20, 78, 70, 0.04);
  cursor: pointer;
  font-size: 12px;
  font-family: var(--font-ios);
  text-align: left;
  color: var(--ink-900);
  transition: background-color 0.18s var(--clay-ease), transform 0.18s var(--clay-ease),
    box-shadow 0.18s var(--clay-ease);
}

.demo-item:hover {
  background: var(--brand-50);
  box-shadow: 0 1px 2px rgba(20, 78, 70, 0.04), 0 8px 16px -14px rgba(47, 157, 146, 1);
}

.demo-item:active {
  transform: scale(0.985);
}

.demo-role {
  flex: none;
  color: var(--ink-500);
}

.demo-account {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 600;
  color: var(--brand-700);
}

.demo-name {
  margin-left: auto;
  color: var(--ink-400);
}

.footnote {
  position: relative;
  z-index: 2;
  max-width: 680px;
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 11.5px;
  line-height: 1.7;
  text-align: center;
  color: var(--ink-500);
  background: rgba(255, 255, 255, 0.6);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(10px);
  transition: opacity 0.35s var(--clay-ease);
}

.is-immersive .footnote {
  opacity: 0;
  pointer-events: none;
}

/* ---------------- 隐藏 / 显示登录界面 ---------------- */

.ui-toggle {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink-700);
  font-family: var(--font-ios);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  backdrop-filter: blur(12px);
  box-shadow:
    0 10px 24px -16px rgba(20, 78, 70, 0.7),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  transition: background-color 0.22s var(--clay-ease), transform 0.18s var(--clay-ease),
    box-shadow 0.22s var(--clay-ease);
}

.ui-toggle:hover {
  background: rgba(255, 255, 255, 0.9);
}

.ui-toggle:active {
  transform: scale(0.97);
}

.ui-toggle .el-icon {
  font-size: 15px;
  color: var(--brand-600);
}

.is-immersive .ui-toggle {
  background: rgba(255, 255, 255, 0.84);
}

/* 中等宽度下卡片几乎占满屏幕，悬浮按钮会压住表单，只在宽屏提供沉浸模式 */
@media (max-width: 1279px) {
  .ui-toggle {
    display: none;
  }
}

/* ---------------- 响应式 ---------------- */

@media (max-width: 940px) {
  .login {
    padding: 24px 16px;
  }

  .login-card {
    grid-template-columns: 1fr;
    border-radius: 28px;
  }

  .brand-panel {
    gap: 14px;
    padding: 26px 24px 22px;
  }

  .brand-main h1 {
    margin-bottom: 6px;
    font-size: 23px;
  }

  .brand-main p {
    max-width: none;
    font-size: 12.5px;
  }

  .art,
  .flow {
    display: none;
  }

  .form-panel {
    padding: 24px 24px 30px;
  }

  .form-card {
    max-width: none;
  }

  .form-head h2 {
    font-size: 22px;
  }

  .form-head p {
    min-height: 0;
  }

  .portals {
    margin-bottom: 20px;
  }
}

@media (max-width: 560px) {
  .login {
    padding: 0;
    gap: 0;
  }

  .login-card {
    min-height: 100vh;
    border-radius: 0;
    box-shadow: none;
  }

  .brand-panel {
    padding: calc(26px + env(safe-area-inset-top)) 20px 20px;
  }

  .brand-main h1 {
    font-size: 20px;
  }

  .form-panel {
    align-items: flex-start;
    padding: 20px 20px calc(40px + env(safe-area-inset-bottom));
  }

  .portal {
    padding: 8px 2px 7px;
    font-size: 12px;
  }

  .footnote {
    display: none;
  }
}
</style>
