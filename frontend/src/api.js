import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      if (body.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        if (location.hash !== '#/login') location.hash = '#/login'
      }
      throw new Error(body.msg || '请求失败')
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.msg || err.message || '网络异常'
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.hash !== '#/login') location.hash = '#/login'
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export const api = {
  login: (username, password) => http.post('/auth/login', { username, password }),
  me: () => http.get('/auth/me'),

  labs: () => http.get('/labs'),
  labProgress: () => http.get('/labs/progress'),
  tutors: () => http.get('/tutors'),
  tutorDetail: (id) => http.get(`/tutors/${id}`),

  myApplication: () => http.get('/student/application'),
  apply: (payload) => http.post('/student/apply', payload),
  myOffers: () => http.get('/student/offers'),
  confirmOffer: (id) => http.post(`/student/offers/${id}/confirm`),
  abandonOffer: (id) => http.post(`/student/offers/${id}/abandon`),
  myResult: () => http.get('/student/result'),

  tutorMe: () => http.get('/tutor/me'),
  applicants: () => http.get('/tutor/applicants'),
  score: (payload) => http.post('/tutor/score', payload),
  decide: (payload) => http.post('/tutor/decide', payload),

  labApplications: () => http.get('/lab/applications'),
  labSchedules: () => http.get('/lab/schedules'),
  createSchedule: (payload) => http.post('/lab/schedules', payload),

  overview: () => http.get('/admin/overview'),
  rounds: () => http.get('/admin/rounds'),
  switchRound: (roundNo) => http.post(`/admin/rounds/${roundNo}/switch`),
  sweep: () => http.post('/admin/sweep'),
  adjustPreview: () => http.post('/admin/adjust/preview'),
  adjustRun: () => http.post('/admin/adjust/run'),
  students: () => http.get('/admin/students'),
  adminResult: () => http.get('/admin/result'),

  notifications: () => http.get('/notifications'),
  readAll: () => http.post('/notifications/read-all')
}

export default http
