import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user.js'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    children: [
      // 学生端
      { path: 'student/tutors', component: () => import('../views/student/Tutors.vue'), meta: { caps: ['STUDENT'], title: '导师与方向' } },
      { path: 'student/application', component: () => import('../views/student/Application.vue'), meta: { caps: ['STUDENT'], title: '我的志愿' } },
      { path: 'student/resume', component: () => import('../views/student/Resume.vue'), meta: { caps: ['STUDENT'], title: '我的简历' } },
      { path: 'student/offers', component: () => import('../views/student/Offers.vue'), meta: { caps: ['STUDENT'], title: '我的 Offer' } },
      { path: 'student/result', component: () => import('../views/student/Result.vue'), meta: { caps: ['STUDENT'], title: '录取结果' } },

      // 导师 + 实训室管理员合并工作台
      { path: 'workbench/applicants', component: () => import('../views/workbench/Applicants.vue'), meta: { caps: ['TUTOR_WORK'], title: '报考我的学生' } },
      { path: 'workbench/offers', component: () => import('../views/workbench/Offers.vue'), meta: { caps: ['TUTOR_WORK'], title: '我的录取' } },
      { path: 'workbench/schedules', component: () => import('../views/workbench/Schedules.vue'), meta: { caps: ['LAB_MANAGE'], title: '面试排期' } },
      { path: 'workbench/progress', component: () => import('../views/workbench/Progress.vue'), meta: { caps: ['LAB_MANAGE'], title: '本室招生进度' } },
      { path: 'workbench/lab-adjust', component: () => import('../views/workbench/LabAdjust.vue'), meta: { caps: ['LAB_MANAGE'], title: '室内调剂' } },

      // 管理端
      { path: 'admin/dashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { caps: ['ADMIN'], title: '全局看板' } },
      { path: 'admin/phases', component: () => import('../views/admin/Phases.vue'), meta: { caps: ['ADMIN'], title: '阶段与轮次' } },
      { path: 'admin/tutors', component: () => import('../views/admin/Tutors.vue'), meta: { caps: ['ADMIN'], title: '导师与实训室' } },
      { path: 'admin/adjust', component: () => import('../views/admin/Adjust.vue'), meta: { caps: ['ADMIN'], title: '统筹调剂' } },
      { path: 'admin/report', component: () => import('../views/admin/Report.vue'), meta: { caps: ['ADMIN'], title: '结果公示' } },

      // 三端共用
      { path: 'messages', component: () => import('../views/workbench/Messages.vue'), meta: { caps: ['STUDENT', 'TUTOR_WORK'], title: '私信' } },
      { path: 'notifications', component: () => import('../views/Notifications.vue'), meta: { title: '消息通知' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!store.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!store.user) {
    try {
      await store.fetchMe()
    } catch {
      store.logout()
      return { path: '/login' }
    }
  }
  if (to.path === '/') {
    return store.homePath
  }
  const required = to.meta.caps
  if (required && !required.some((cap) => store.caps.includes(cap))) {
    return store.homePath
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 实训室双选` : '学生实训室双向选择系统'
})

export default router
