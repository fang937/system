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

/** 与轮次绑定的阶段：填报、面试、确认、分轮录取；其余为全局阶段 */
export const ROUND_SCOPED_CODES = ['APPLY', 'INTERVIEW', 'CONFIRM', 'SUPPLEMENT']

export function isRoundScoped(code) {
  return ROUND_SCOPED_CODES.includes(code)
}

/**
 * 阶段展示名：轮次阶段的名称带上轮次前缀，避免顶栏出现
 * 两条同名「意向填报」时分不清哪条属于当前轮次。
 */
export function phaseLabel(phase) {
  if (!phase) return ''
  return isRoundScoped(phase.code) && phase.roundNo ? `第 ${phase.roundNo} 轮${phase.name}` : phase.name
}

/** 剩余不足 1 小时即视为「即将截止」，统一用红色提示 */
export const URGENT_MS = 60 * 60 * 1000

const pad2 = (n) => String(n).padStart(2, '0')

/** 解析后端时间（yyyy-MM-dd HH:mm:ss / ISO），无效值返回 NaN */
export function toTimestamp(value) {
  if (!value) return NaN
  const text = String(value).trim().replace('T', ' ')
  return new Date(text.replace(/-/g, '/')).getTime()
}

/** 距离截止还剩多少毫秒；无截止时间按 0 处理 */
export function remainingMs(endAt, now = Date.now()) {
  if (!endAt) return 0
  return toTimestamp(endAt) - now
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

/** 三轮录取之后的收尾阶段：不属于任何轮次，round.current 会停在最后一轮 */
export const TAIL_PHASE_CODES = ['ADJUST', 'PUBLISH']

/**
 * 当前所处环节的展示名。
 *
 * <p>统筹调剂（ADJUST）与结果公示（PUBLISH）是全局阶段，不占轮次；
 * 进入这两个阶段后 {@code round.current} 仍停在 3，直接渲染就会出现
 * 「三轮都结束了，学生端还显示第 3 轮」的问题。这里按阶段状态做映射：</p>
 * <ul>
 *   <li>调剂 / 公示进行中 → 显示阶段名；</li>
 *   <li>仍有轮次阶段进行中 → 显示「第 N 轮」；</li>
 *   <li>阶段之间的空档（调剂已结束、公示未开始）→ 沿用最近一个收尾阶段的名称。</li>
 * </ul>
 */
export function currentStageLabel(phases, round) {
  const list = phases || []
  const fallback = round ? `第 ${round} 轮` : ''
  const pick = (code) => list.find((p) => p.code === code) || null
  const isActive = (p) => Boolean(p) && p.state === 'ACTIVE'
  const hasStarted = (p) => Boolean(p) && p.state !== 'PENDING'

  const adjust = pick('ADJUST')
  const publish = pick('PUBLISH')

  // 公示晚于调剂：两者窗口重叠时以公示为准
  if (isActive(publish)) return publish.name || '结果公示'
  if (isActive(adjust)) return adjust.name || '统筹调剂'
  // 仍有阶段在进行中（轮次填报/面试/确认，或前置宣讲）→ 仍按轮次展示
  if (list.some((p) => p.state === 'ACTIVE')) return fallback
  // 空档期：调剂做完但公示还没开，继续显示「统筹调剂」；公示结束后显示「结果公示」
  if (hasStarted(publish)) return publish.name || '结果公示'
  if (hasStarted(adjust)) return adjust.name || '统筹调剂'
  return fallback
}
