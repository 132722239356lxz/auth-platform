import dayjs from 'dayjs'

/**
 * 全局时间格式化 — 统一输出 YYYY-MM-DD HH:mm:ss
 * @param value ISO 时间字符串、时间戳或 dayjs 可解析的任意值
 * @returns 格式化后的时间字符串，无法解析时返回 '-'
 */
export function formatTime(value?: string | number | Date | null): string {
  if (!value) return '-'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : '-'
}

/**
 * 仅日期部分 YYYY-MM-DD
 */
export function formatDate(value?: string | number | Date | null): string {
  if (!value) return '-'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD') : '-'
}
