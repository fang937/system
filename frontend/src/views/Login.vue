<template>
  <div class="login">
    <div class="glow glow-a" />
    <div class="glow glow-b" />
    <div class="glow glow-c" />
    <div class="glow glow-d" />

    <div class="login-card">
      <section class="brand-panel">
        <div class="brand-top">
          <div class="logo">双</div>
          <div>
            <div class="brand-name">学生实训室双向选择系统</div>
            <div class="brand-sub">河北师范大学 · 实训室选组选拔</div>
          </div>
        </div>

        <div class="brand-main">
          <h1>三个入口，各归其位<br />账号属于哪一端，就从哪一端进</h1>
          <p>
            学生填报平行志愿，导师面试打分并锁定名额，实训室负责人统筹本室排期，学院统一控制时间轴与调剂。
            登录入口与账号身份强绑定：走错入口会被后端直接拒绝。
          </p>
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
            <p>{{ currentPortal.desc }}</p>
          </div>

          <el-form :model="form" label-position="top" @submit.prevent>
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
              <span>{{ currentPortal.title }}演示账号</span>
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

    <div class="footnote">
      入口与身份强绑定：学生端仅限学生账号；导师端仅限导师与实训室负责人；管理员端仅限系统管理员。
      实训室负责人身份由管理员在「导师与实训室」中授予。
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user.js'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const form = reactive({ username: '', password: '' })

const portals = [
  {
    key: 'STUDENT',
    title: '学生端',
    icon: 'Reading',
    desc: '填报 3 个平行志愿、确认 Offer、查看录取结果',
    placeholder: '学号'
  },
  {
    key: 'TUTOR',
    title: '导师端',
    icon: 'UserFilled',
    desc: '面试打分与拟录取；实训室负责人还可排期、室内调剂',
    placeholder: '工号'
  },
  {
    key: 'ADMIN',
    title: '管理员端',
    icon: 'OfficeBuilding',
    desc: '阶段轮次、全局看板、导师与实训室、统筹调剂',
    placeholder: '管理员账号'
  }
]

const portal = ref('STUDENT')
const currentPortal = computed(() => portals.find((item) => item.key === portal.value) || portals[0])

const demoAccounts = {
  STUDENT: [
    { role: '学生（空志愿）', username: '2023001', name: '林晓' },
    { role: '学生（已填志愿）', username: '2023002', name: '陈子昂' }
  ],
  TUTOR: [
    { role: '202 实训室负责人 · 刘冠军', username: 'liuguanjun', name: '刘冠军' },
    { role: '204 实训室负责人 · 胡国欣', username: 'huguoxin', name: '胡国欣' },
    { role: '404 实训室导师 · 张会斌', username: 'zhanghuibin', name: '张会斌' },
    { role: '406 实训室负责人 · 杨振樱', username: 'yangzhenying', name: '杨振樱' },
    { role: '505 实训室负责人 · 王浩', username: 'wanghao', name: '王浩' }
  ],
  ADMIN: [{ role: '系统管理员', username: 'admin', name: '张建国' }]
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
  gap: 18px;
  padding: 32px 20px;
  overflow: hidden;
  background: var(--bg-grouped);
}

/* 背景柔光 */
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}

.glow-a {
  width: 560px;
  height: 560px;
  top: -180px;
  right: -120px;
  background: rgba(0, 122, 255, 0.22);
}

.glow-b {
  width: 480px;
  height: 480px;
  bottom: -200px;
  left: -140px;
  background: rgba(175, 82, 222, 0.16);
}

.glow-c {
  width: 380px;
  height: 380px;
  top: 38%;
  left: -180px;
  background: rgba(48, 176, 199, 0.16);
}

.glow-d {
  width: 340px;
  height: 340px;
  bottom: -140px;
  right: -100px;
  background: rgba(255, 149, 0, 0.12);
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1020px;
  display: grid;
  grid-template-columns: 1.02fr 1fr;
  background: var(--surface);
  border-radius: 32px;
  box-shadow: var(--shadow-lg), 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  animation: login-in 0.6s var(--ease-out) both;
}

@keyframes login-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------------- 品牌区 ---------------- */

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 30px;
  padding: 46px 46px 42px;
  color: #fff;
  background: linear-gradient(158deg, #0a84ff 0%, #0066cc 48%, #3b3fd6 100%);
  overflow: hidden;
}

.brand-panel::before {
  content: '';
  position: absolute;
  width: 380px;
  height: 380px;
  right: -140px;
  bottom: -170px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.09);
}

.brand-panel::after {
  content: '';
  position: absolute;
  width: 220px;
  height: 220px;
  right: 40px;
  top: -110px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}

.brand-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.brand-sub {
  margin-top: 3px;
  font-size: 12px;
  opacity: 0.74;
}

.brand-main {
  position: relative;
  z-index: 1;
  margin-top: 4px;
}

.brand-main h1 {
  margin: 0 0 16px;
  font-size: 28px;
  line-height: 1.45;
  font-weight: 650;
  letter-spacing: -0.025em;
}

.brand-main p {
  margin: 0;
  max-width: 460px;
  font-size: 13.5px;
  line-height: 1.9;
  opacity: 0.82;
}

.flow {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}

.flow-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(8px);
}

.flow-index {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: var(--ios-blue-dark);
  background: rgba(255, 255, 255, 0.92);
}

/* ---------------- 表单区 ---------------- */

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 44px 48px;
}

.form-card {
  width: 100%;
  max-width: 372px;
}

/* 三端入口分段控件 */
.portals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2px;
  padding: 3px;
  margin-bottom: 26px;
  background: var(--fill-1);
  border-radius: 14px;
}

.portal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 9px 4px 8px;
  border: none;
  border-radius: 11px;
  background: transparent;
  color: var(--label-2);
  font-family: var(--font-ios);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.24s var(--ease-ios), color 0.24s var(--ease-ios),
    box-shadow 0.24s var(--ease-ios), transform 0.18s var(--ease-ios);
}

.portal:active {
  transform: scale(0.97);
}

.portal-icon {
  font-size: 17px;
  opacity: 0.85;
}

.portal.active {
  background: var(--surface);
  color: var(--label-1);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 1px rgba(0, 0, 0, 0.04);
}

.portal.active .portal-icon {
  color: var(--ios-blue);
  opacity: 1;
}

.form-head h2 {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.028em;
}

.form-head p {
  margin: 0 0 24px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--label-2);
  min-height: 42px;
}

.submit {
  width: 100%;
  height: 50px;
  margin-top: 4px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
}

.demo {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--separator);
}

.demo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--label-2);
}

.demo-hint {
  color: var(--label-3);
}

.demo-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  border-radius: 14px;
  background: var(--fill-2);
}

.demo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 11px;
  border: none;
  border-radius: 10px;
  background: var(--surface);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  cursor: pointer;
  font-size: 12px;
  font-family: var(--font-ios);
  text-align: left;
  color: var(--label-1);
  transition: background-color 0.18s var(--ease-ios), transform 0.18s var(--ease-ios);
}

.demo-item:hover {
  background: rgba(0, 122, 255, 0.06);
}

.demo-item:active {
  transform: scale(0.985);
}

.demo-role {
  flex: none;
  color: var(--label-2);
}

.demo-account {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--label-1);
}

.demo-name {
  margin-left: auto;
  color: var(--label-3);
}

.footnote {
  position: relative;
  z-index: 1;
  max-width: 660px;
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
  color: var(--label-3);
}

/* ---------------- 响应式 ---------------- */

@media (max-width: 900px) {
  .login {
    padding: 24px 16px;
  }

  .login-card {
    grid-template-columns: 1fr;
    border-radius: 26px;
  }

  .brand-panel {
    gap: 16px;
    padding: 26px 24px 24px;
  }

  .brand-main h1 {
    margin: 0;
    font-size: 19px;
    line-height: 1.5;
  }

  .brand-main p {
    display: none;
  }

  .flow {
    display: none;
  }

  .form-panel {
    padding: 22px 24px 28px;
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
    padding: calc(26px + env(safe-area-inset-top)) 20px 22px;
  }

  .brand-main h1 {
    font-size: 17px;
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
