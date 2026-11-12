/**
 * 多色调分配：同一类元素按名称/ID 稳定映射到 iOS 系统色，
 * 刷新不变、多端一致；颜色只用于小面积（图标、圆点、浅底、进度），主体仍保持中性。
 */
const TONES = ['blue', 'indigo', 'purple', 'teal', 'green', 'orange', 'pink']

/** 按任意 key 稳定取色 */
export function toneByKey(key) {
  if (key === null || key === undefined || key === '') return 'blue'
  const text = String(key)
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 9973
  }
  return TONES[hash % TONES.length]
}

/** 实训室优先按名称语义配色，其余按 key 散列 */
export function labTone(labId, labName = '') {
  const name = String(labName)
  if (/人工智能|智能|AI/i.test(name)) return 'blue'
  if (/大数据|数据/.test(name)) return 'purple'
  if (/安全|网络/.test(name)) return 'teal'
  if (/软件|工程/.test(name)) return 'indigo'
  return toneByKey(labId)
}

/** 名额使用率的语义色：宽松绿、偏紧橙、紧张红 */
export function usageTone(percent) {
  if (percent >= 90) return 'red'
  if (percent >= 60) return 'orange'
  return 'green'
}
