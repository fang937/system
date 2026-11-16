export const offerStatusMap = {
  0: { label: '待处理', type: 'info' },
  1: { label: '拟录取', type: 'success' },
  2: { label: '递补', type: 'warning' },
  3: { label: '淘汰', type: 'info' }
}

export const confirmStatusMap = {
  0: { label: '待确认', type: 'warning' },
  1: { label: '已确认', type: 'success' },
  2: { label: '超时放弃', type: 'danger' },
  3: { label: '已放弃', type: 'info' }
}

export const applicationStatusMap = {
  SUBMITTED: { label: '已提交', type: 'primary' },
  MATCHED: { label: '已录取', type: 'success' },
  REJECTED: { label: '未录取', type: 'info' },
  EXPIRED: { label: '已失效', type: 'info' }
}

export const adjustTypeMap = {
  1: '实训室内调剂',
  2: '全院调剂',
  3: '人工兜底'
}

export const phaseStateMap = {
  PENDING: { label: '未开始', type: 'info' },
  ACTIVE: { label: '进行中', type: 'success' },
  CLOSED: { label: '已结束', type: 'info' }
}

export function fmtTime(value, withSeconds = false) {
  if (!value) return '—'
  const text = String(value).replace('T', ' ')
  return withSeconds ? text.slice(0, 19) : text.slice(0, 16)
}

/** 剩余不足 1 小时即视为「即将截止」，统一用红色提示 */
export const URGENT_MS = 60 * 60 * 1000

const pad2 = (n) => String(n).padStart(2, '0')

/** 距离截止还剩多少毫秒；无截止时间按 0 处理 */
export function remainingMs(endAt, now = Date.now()) {
  if (!endAt) return 0
  return new Date(String(endAt).replace(/-/g, '/')).getTime() - now
}

/** 是否处于「剩余不足 1 小时」的紧张区间（已结束不算） */
export function isUrgent(endAt, now = Date.now()) {
  const diff = remainingMs(endAt, now)
  return diff > 0 && diff < URGENT_MS
}

export function countdown(endAt, now = Date.now()) {
  if (!endAt) return ''
  const diff = remainingMs(endAt, now)
  if (diff <= 0) return '已结束'
  const d = Math.floor(diff / 86400000)
  const h = Math.floor((diff % 86400000) / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  return `${d > 0 ? d + ' 天 ' : ''}${pad2(h)}:${pad2(m)}`
}

/** 顶栏用：精确到秒；超过 1 天时省略秒，避免多阶段并排时过宽 */
export function countdownClock(endAt, now = Date.now()) {
  if (!endAt) return ''
  const diff = remainingMs(endAt, now)
  if (diff <= 0) return '已结束'
  const d = Math.floor(diff / 86400000)
  const h = Math.floor((diff % 86400000) / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  return d > 0 ? `${d} 天 ${pad2(h)}:${pad2(m)}` : `${pad2(h)}:${pad2(m)}:${pad2(s)}`
}

export function roundLabel(roundNo) {
  return roundNo ? `第 ${roundNo} 轮` : '全局'
}
