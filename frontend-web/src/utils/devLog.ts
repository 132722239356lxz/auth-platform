/**
 * 开发模式调试日志
 * 仅在 import.meta.env.DEV（npm run dev）下生效，
 * 用于记录真实的 HTTP 请求/参数与页面跳转，便于前后端联调排查。
 * 生产构建会被 import.meta.env.DEV 优化掉，不影响线上性能。
 */

const enabled = import.meta.env.DEV

/** 需要脱敏的请求头（避免 token 明文出现在控制台） */
const SENSITIVE_HEADERS = ['authorization', 'x-user']

function mask(value: string): string {
  if (!value) return value
  if (value.length <= 8) return '***'
  return value.slice(0, 4) + '***' + value.slice(-4)
}

function safeHeaders(headers?: Record<string, any>): Record<string, any> {
  if (!headers) return {}
  const result: Record<string, any> = {}
  for (const key of Object.keys(headers)) {
    const lower = key.toLowerCase()
    if (SENSITIVE_HEADERS.includes(lower)) {
      result[key] = mask(String(headers[key]))
    } else {
      result[key] = headers[key]
    }
  }
  return result
}

function tryParse(value: unknown): unknown {
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

/** 记录发出的请求 */
export function logRequest(config: any): void {
  if (!enabled || !config) return
  const { method, url, params, data, headers } = config
  console.groupCollapsed(
    `%cREQ%c ${String(method || 'GET').toUpperCase()} ${url}`,
    'color:#fff;background:#409EFF;padding:2px 5px;border-radius:3px;font-size:11px',
    'color:#409EFF;font-weight:bold'
  )
  console.log('URL:', url)
  console.log('Method:', String(method || 'GET').toUpperCase())
  if (params && Object.keys(params).length) console.log('Params:', params)
  if (data !== undefined && data !== '') console.log('Body:', tryParse(data))
  console.log('Headers:', safeHeaders(headers))
  console.groupEnd()
}

/** 记录响应结果 */
export function logResponse(response: any, duration?: number): void {
  if (!enabled || !response) return
  const { status, config, data } = response
  const isBlob = config?.responseType === 'blob'
  console.groupCollapsed(
    `%cRES%c ${status} ${config?.url}`,
    'color:#fff;background:#67C23A;padding:2px 5px;border-radius:3px;font-size:11px',
    'color:#67C23A;font-weight:bold'
  )
  console.log('Status:', status)
  if (typeof duration === 'number') console.log('Duration:', `${duration}ms`)
  if (!isBlob) console.log('Data:', data)
  console.groupEnd()
}

/** 记录页面跳转 */
export function logRoute(from: any, to: any, duration?: number): void {
  if (!enabled) return
  console.groupCollapsed(
    `%cROUTE%c ${from?.fullPath || '(start)'} → ${to?.fullPath}`,
    'color:#fff;background:#E6A23C;padding:2px 5px;border-radius:3px;font-size:11px',
    'color:#E6A23C;font-weight:bold'
  )
  console.log('From:', from?.name, from?.fullPath)
  console.log('To:', to?.name, to?.fullPath, `(${to?.meta?.title || ''})`)
  if (typeof duration === 'number') console.log('Duration:', `${duration}ms`)
  console.groupEnd()
}
