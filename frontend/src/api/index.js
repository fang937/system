import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function toLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (!location.pathname.endsWith('/login')) {
    location.href = '/login'
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body instanceof Blob) {
      return body
    }
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 401) {
        toLogin()
      }
      if (!response.config?.skipErrorToast) {
        ElMessage.error(body.message || '请求失败')
      }
      const error = new Error(body.message || '请求失败')
      error.code = body.code
      error.data = body.data
      return Promise.reject(error)
    }
    return body
  },
  async (error) => {
    const status = error.response?.status
    let payload = error.response?.data
    // 下载接口是 blob 模式，错误体会是 Blob，需要先解析出 message
    if (payload instanceof Blob) {
      try {
        payload = JSON.parse(await payload.text())
      } catch {
        payload = null
      }
    }
    const message = payload?.message || error.message || '网络异常'
    if (status === 401) {
      toLogin()
    }
    if (!error.config?.skipErrorToast) {
      ElMessage.error(message)
    }
    const wrapped = new Error(message)
    wrapped.code = payload?.code || status
    wrapped.data = payload?.data
    return Promise.reject(wrapped)
  }
)

export const api = {
  // 统一登录
  login: (payload) => http.post('/auth/login', payload),
  me: () => http.get('/auth/me'),

  // 公共元数据
  phase: () => http.get('/meta/phase'),
  labs: () => http.get('/meta/labs'),
  allTutors: () => http.get('/meta/tutors'),

  // 学生端
  profile: () => http.get('/student/profile'),
  studentTutors: () => http.get('/student/tutors'),
  myApplication: () => http.get('/student/application'),
  submitApplication: (payload) => http.put('/student/application', payload),
  myOffers: () => http.get('/student/offers'),
  confirmOffer: (id) => http.post(`/student/offers/${id}/confirm`),
  declineOffer: (id) => http.post(`/student/offers/${id}/decline`),
  myResult: () => http.get('/student/result'),

  // 工作台（导师 + 实训室管理员）
  workbenchOverview: () => http.get('/workbench/overview'),
  applicants: (round) => http.get('/workbench/applicants', { params: { round } }),
  saveScore: (applicationId, payload) => http.post(`/workbench/applicants/${applicationId}/score`, payload),
  decide: (applicationId, payload) => http.post(`/workbench/applicants/${applicationId}/decision`, payload),
  workbenchOffers: (round) => http.get('/workbench/offers', { params: { round } }),
  schedules: (round) => http.get('/workbench/schedules', { params: { round } }),
  createSchedule: (payload) => http.post('/workbench/schedules', payload),
  updateSchedule: (id, payload) => http.put(`/workbench/schedules/${id}`, payload),
  deleteSchedule: (id) => http.delete(`/workbench/schedules/${id}`),
  labProgress: (round) => http.get('/workbench/lab/progress', { params: { round } }),
  labStudents: (round) => http.get('/workbench/lab/students', { params: { round } }),
  labPool: () => http.get('/workbench/lab/pool'),
  labVacancies: () => http.get('/workbench/lab/vacancies'),
  labAdjust: (payload) => http.post('/workbench/lab/adjust', payload),

  // 管理端
  adminOverview: () => http.get('/admin/overview'),
  phases: () => http.get('/admin/phases'),
  updatePhase: (id, payload) => http.put(`/admin/phases/${id}`, payload),
  jumpPhase: (id) => http.post(`/admin/phases/${id}/jump`),
  closePhase: (id) => http.post(`/admin/phases/${id}/close`),
  setRound: (round) => http.post(`/admin/rounds/${round}`),
  adminTutors: () => http.get('/admin/tutors'),
  createTutor: (payload) => http.post('/admin/tutors', payload),
  updateTutor: (id, payload) => http.put(`/admin/tutors/${id}`, payload),
  toggleTutor: (id, enable) => http.post(`/admin/tutors/${id}/toggle`, null, { params: { enable } }),
  changeTutorPosition: (id, position) => http.post(`/admin/tutors/${id}/position`, { position }),
  adminLabs: () => http.get('/admin/labs'),
  createLab: (payload) => http.post('/admin/labs', payload),
  updateLab: (id, payload) => http.put(`/admin/labs/${id}`, payload),
  students: () => http.get('/admin/students'),
  adjustPreview: () => http.get('/admin/adjust/preview'),
  adjustRun: (scope) => http.post('/admin/adjust/run', null, { params: { scope } }),
  adjustManual: (payload) => http.post('/admin/adjust/manual', payload),
  results: () => http.get('/admin/results'),
  exportResults: () => http.get('/admin/results/export', { responseType: 'blob' }),

  // 通知
  notifications: () => http.get('/notifications'),
  readNotification: (id) => http.post(`/notifications/${id}/read`),
  readAllNotifications: () => http.post('/notifications/read-all'),

  // 简历
  studentResume: () => http.get('/student/resume'),
  uploadResume: (file, onProgress) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/student/resume', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => onProgress && onProgress(event),
      skipErrorToast: true
    })
  },
  deleteResume: () => http.delete('/student/resume'),
  studentResumeBlob: () => http.get('/student/resume/file', { responseType: 'blob' }),
  applicantResumeBlob: (applicationId) =>
    http.get(`/workbench/applicants/${applicationId}/resume`, { responseType: 'blob', skipErrorToast: true }),

  // AI 能力（当前默认规则引擎占位，填 AI_API_KEY 后即为真实模型）
  // AI 调用统一 skipErrorToast：失败由页面内的红色提示框承接，避免重复打扰
  aiRubric: () => http.get('/ai/rubric'),
  aiInterviewTips: () => http.post('/ai/interview-tips', null, { skipErrorToast: true }),
  aiRecommendations: () => http.get('/ai/recommendations', { skipErrorToast: true }),
  aiAvatars: () => http.get('/ai/avatars'),
  aiAvatarChat: (tutorId, payload) => http.post(`/ai/avatars/${tutorId}/chat`, payload, { skipErrorToast: true }),
  aiResumeReview: (applicationId) => http.post('/ai/resume-review', { applicationId }, { skipErrorToast: true }),
  aiProviders: () => http.get('/ai/providers'),
  aiActivateProvider: (name) => http.post(`/ai/providers/${name}/activate`),

  // 多维评分（按 score_rubrics 权重折算总分）
  saveScores: (applicationId, items) => http.post(`/workbench/applicants/${applicationId}/scores`, { items })
}

export default http
