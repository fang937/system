import { defineStore } from 'pinia'
import { api } from '../api/index.js'
import { isRoundScoped } from '../utils/labels.js'

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
    currentRound: (state) => state.phase?.round || 1,
    /**
     * 与当前轮次匹配的阶段：轮次相关的阶段（填报/面试/确认/录取）只保留当前轮次那一份，
     * 全局阶段（宣讲/开放日/调剂/公示）原样保留。
     *
     * 顶部阶段条与「当前阶段」提示都必须用它，不能再直接遍历 phases：
     * 各轮时间窗可能重叠，否则切到第 2、3 轮后仍会把第 1 轮的同名阶段排在前面。
     */
    roundPhases: (state) => {
      const round = state.phase?.round
      return (state.phase?.phases || []).filter(
        (p) => !isRoundScoped(p.code) || Number(p.roundNo) === Number(round)
      )
    },
    /**
     * 指定阶段当前是否开放。
     * 阶段时间窗可能重叠，不能只看 currentPhase（它只取业务顺序最大的那条），
     * 否则并行期间会出现「顶栏显示进行中、页面却判定未开放」的矛盾。
     * 轮次相关阶段（APPLY/INTERVIEW/CONFIRM）按当前轮次匹配，全局阶段忽略轮次。
     */
    phaseActive: (state) => (code) => {
      const round = state.phase?.round
      return (state.phase?.phases || []).some(
        (p) =>
          p.code === code &&
          p.state === 'ACTIVE' &&
          (p.roundNo === null || p.roundNo === undefined || Number(p.roundNo) === Number(round))
      )
    }
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
