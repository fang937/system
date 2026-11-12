import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue'), meta: { public: true } },
  { path: '/', redirect: () => (localStorage.getItem('token') ? '/home' : '/login') },
  { path: '/home', component: () => import('./views/Home.vue'), meta: { title: '首页' } },
  { path: '/tutors', component: () => import('./views/Tutors.vue'), meta: { title: '导师与志愿', roles: ['STUDENT'] } },
  { path: '/my-selection', component: () => import('./views/MySelection.vue'), meta: { title: '我的双选', roles: ['STUDENT'] } },
  { path: '/tutor-desk', component: () => import('./views/TutorDesk.vue'), meta: { title: '导师工作台', roles: ['TUTOR'] } },
  { path: '/lab-desk', component: () => import('./views/LabDesk.vue'), meta: { title: '实训室管理', roles: ['LAB_ADMIN', 'SYS_ADMIN'] } },
  { path: '/admin-desk', component: () => import('./views/AdminDesk.vue'), meta: { title: '系统管理', roles: ['SYS_ADMIN'] } },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (!to.meta.public && !token) return '/login'
  if (to.meta.roles && user && !to.meta.roles.includes(user.role)) return '/home'
  if (to.path === '/login' && token) return '/home'
  return true
})

export default router
