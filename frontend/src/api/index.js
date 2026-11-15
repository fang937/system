import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 超时预算分层：
 * - 普通接口 30s，够用且能快速暴露后端异常；
 * - AI 对话 90s：首次调用含 DNS/TLS 握手与模型冷启动，30s 不够；
 * - 简历初评 360s：首次评一份扫描件简历时，后端要先同步跑完 OCR
 *   （MinerU 轮询上限 120s，失败再降级 PP-StructureV3 上限 180s），再进模型。
 *   此前全局 20s，等于「第一次点必然超时」。上传时已后台预解析正文，
 *   正常情况下点评直接命中缓存，几秒返回；这里的 360s 只是兜底。
 * - 文件上传 120s：10MB 简历在弱网下也会超过 30s。
 */
const DEFAULT_TIMEOUT = 30000
const AI_TIMEOUT = 90000
const AI_REVIEW_TIMEOUT = 360000
const AI_OPTIMIZE_TIMEOUT = 180000
const UPLOAD_TIMEOUT = 120000

const http = axios.create({ baseURL: '/api', timeout: DEFAULT_TIMEOUT })

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

/** axios 的超时在浏览器里表现为 ECONNABORTED / "timeout of Nms exceeded" */
function isTimeoutError(error) {
  if (!error) return false
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return true
  return /timeout of \d+ms exceeded/i.test(error.message || '')
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
    const timedOut = isTimeoutError(error)
    const message = payload?.message
      || (timedOut
        // 不暴露 "timeout of 20000ms" 这类实现细节，改为可执行的中文提示
        ? '等待超时：服务端可能仍在处理，稍后重试通常即可'
        : error.message || '网络异常')
    if (status === 401) {
      toLogin()
    }
    if (!error.config?.skipErrorToast) {
      ElMessage.error(message)
    }
    const wrapped = new Error(message)
    wrapped.code = payload?.code || status
    wrapped.data = payload?.data
    wrapped.timeout = timedOut
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
  resetPhases: () => http.post('/admin/phases/reset'),
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
      timeout: UPLOAD_TIMEOUT,
      onUploadProgress: (event) => onProgress && onProgress(event),
      skipErrorToast: true
    })
  },
  deleteResume: () => http.delete('/student/resume'),
  studentResumeBlob: () => http.get('/student/resume/file', { responseType: 'blob' }),
  applicantResumeBlob: (applicationId) =>
    http.get(`/workbench/applicants/${applicationId}/resume`, { responseType: 'blob', skipErrorToast: true }),

  // 结构化简历草稿（AI 优化简历的编辑底稿）
  resumeDraft: () => http.get('/student/resume/draft'),
  saveResumeDraft: (payload) => http.put('/student/resume/draft', payload, { skipErrorToast: true }),
  importResumeDraft: () =>
    http.post('/student/resume/draft/import', null, { skipErrorToast: true, timeout: AI_REVIEW_TIMEOUT }),

  // AI 能力（默认走管理端配置的通道，一把 Key 都没有时为内置规则引擎）
  // AI 调用统一 skipErrorToast：失败由页面内的红色提示框承接，避免重复打扰
  // 超时单独放宽：AI 属于长任务，不能用普通接口的 30s
  aiRubric: () => http.get('/ai/rubric'),
  aiInterviewTips: () => http.post('/ai/interview-tips', null, { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiRecommendations: () => http.get('/ai/recommendations', { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiAvatars: () => http.get('/ai/avatars'),
  aiAvatarChat: (tutorId, payload) =>
    http.post(`/ai/avatars/${tutorId}/chat`, payload, { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiResumeReview: (applicationId) =>
    http.post('/ai/resume-review', { applicationId }, { skipErrorToast: true, timeout: AI_REVIEW_TIMEOUT }),
  aiResumeOptimize: (payload) =>
    http.post('/ai/resume-optimize', payload, { skipErrorToast: true, timeout: AI_OPTIMIZE_TIMEOUT }),
  aiCoachQuestion: (payload) =>
    http.post('/ai/interview-coach/question', payload, { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiCoachFeedback: (payload) =>
    http.post('/ai/interview-coach/feedback', payload, { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiCoachHistory: () => http.get('/ai/interview-coach/history'),
  aiProviders: () => http.get('/ai/providers'),
  aiActivateProvider: (name) => http.post(`/ai/providers/${name}/activate`),
  aiProviderModels: (name) => http.get(`/ai/providers/${name}/models`, { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiProviderTest: (name) =>
    http.post(`/ai/providers/${name}/test`, null, { skipErrorToast: true, timeout: AI_TIMEOUT }),

  // 导师端：自己的 AI 分身（状态 / 设定 / 试聊）
  myAvatar: () => http.get('/ai/my-avatar'),
  saveMyAvatar: (payload) => http.put('/ai/my-avatar', payload),
  myAvatarChat: (question) =>
    http.post('/ai/my-avatar/chat', { question }, { skipErrorToast: true, timeout: AI_TIMEOUT }),
  aiSetProviderModel: (name, model) => http.put(`/ai/providers/${name}/model`, { model }),
  aiSetProviderKey: (name, apiKey) => http.put(`/ai/providers/${name}/key`, { apiKey }),
  ocrConfig: () => http.get('/admin/ocr-config'),
  updateOcrConfig: (provider) => http.put('/admin/ocr-config', { provider }),
  updateOcrSecret: (target, value) => http.put('/admin/ocr-secrets', { target, value }),

  // 师生直连私信（学生 ↔ 导师/负责人）
  messageThreads: () => http.get('/messages/threads'),
  messageConversation: (userId) => http.get(`/messages/with/${userId}`),
  messageSend: (userId, content) => http.post(`/messages/with/${userId}`, { content }),

  // 多维评分（按 score_rubrics 权重折算总分）
  saveScores: (applicationId, items) => http.post(`/workbench/applicants/${applicationId}/scores`, { items })
}

export default http
