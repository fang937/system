import { defineStore } from 'pinia'
import { api } from '../api/index.js'

const CAP_HOME = {
  STUDENT: '/student/tutors',
  TUTOR_WORK: '/workbench/applicants',
  LAB_MANAGE: '/workbench/progress',
  ADMIN: '/admin/dashboard'
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    phase: null
  }),

  getters: {
    isLogin: (state) => Boolean(state.token),
    caps: (state) => state.user?.caps || [],
    role: (state) => state.user?.role || '',
    hasTutorWork: (state) => (state.user?.caps || []).includes('TUTOR_WORK'),
    hasLabManage: (state) => (state.user?.caps || []).includes('LAB_MANAGE'),
    isAdmin: (state) => (state.user?.caps || []).includes('ADMIN'),
    isStudent: (state) => (state.user?.caps || []).includes('STUDENT'),
    homePath() {
      for (const cap of this.caps) {
        if (CAP_HOME[cap]) return CAP_HOME[cap]
      }
      return '/login'
    },
    currentPhase: (state) => state.phase?.phase || null,
    currentRound: (state) => state.phase?.round || 1
  },

  actions: {
    async login(username, password, portal) {
      const data = await api.login({ username, password, portal })
      this.token = data.token
      this.user = data.user
      this.phase = data.phase
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      return data.user
    },

    async fetchMe() {
      const data = await api.me()
      this.user = data.user
      this.phase = data.phase
      localStorage.setItem('user', JSON.stringify(data.user))
      return data.user
    },

    async refreshPhase() {
      try {
        this.phase = await api.phase()
      } catch {
        /* 阶段刷新失败不阻塞页面 */
      }
    },

    logout() {
      this.token = ''
      this.user = null
      this.phase = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
