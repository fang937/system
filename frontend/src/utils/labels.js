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

export function countdown(endAt) {
  if (!endAt) return ''
  const end = new Date(String(endAt).replace(/-/g, '/')).getTime()
  const diff = end - Date.now()
  if (diff <= 0) return '已结束'
  const d = Math.floor(diff / 86400000)
  const h = Math.floor((diff % 86400000) / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  return `${d > 0 ? d + ' 天 ' : ''}${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

export function roundLabel(roundNo) {
  return roundNo ? `第 ${roundNo} 轮` : '全局'
}
