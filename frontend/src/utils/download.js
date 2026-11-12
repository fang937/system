/** 把 Blob 存成本地文件：用于需要鉴权的下载接口（不能直接开新窗口） */
export function saveBlob(blob, filename = 'download') {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 人类可读的文件大小 */
export function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '—'
  const value = Number(bytes)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}
