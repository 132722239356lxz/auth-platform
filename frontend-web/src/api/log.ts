import { get, post, put, del } from '@/utils/request'
import type { R, LogQueryRequest, LogRecord, LogStatsDTO, SolutionSaveRequest } from '@/types'

const LOG_BASE = '/log-server'

// ==================== 日志查询 ====================

export function queryLogs(data: LogQueryRequest): Promise<R<{ total: number; list: LogRecord[] }>> {
  return post(`${LOG_BASE}/api/logs/query`, data)
}

export function getLogStats(): Promise<R<LogStatsDTO>> {
  return get(`${LOG_BASE}/api/logs/stats`)
}

export function cleanLogs(retentionDays?: number): Promise<R<void>> {
  return del(`${LOG_BASE}/api/logs/clean`, { retentionDays: retentionDays || 30 })
}

// ==================== AI 日志分析 ====================

export function analyzeByFingerprint(fingerprint: string): Promise<R<any>> {
  return get(`${LOG_BASE}/api/logs/analysis/fingerprint/${fingerprint}`)
}

export function analyzeByTraceId(traceId: string): Promise<R<any>> {
  return get(`${LOG_BASE}/api/logs/analysis/trace/${traceId}`)
}

export function searchSimilarErrors(exceptionType: string): Promise<R<any>> {
  return get(`${LOG_BASE}/api/logs/analysis/similar`, { exceptionType })
}

export function saveSolution(data: SolutionSaveRequest): Promise<R<any>> {
  return post(`${LOG_BASE}/api/logs/analysis/solution`, data)
}

export function getSolutionByFingerprint(fingerprint: string): Promise<R<any>> {
  return get(`${LOG_BASE}/api/logs/analysis/solution/${fingerprint}`)
}

export function markSolutionResolved(fingerprint: string): Promise<R<void>> {
  return put(`${LOG_BASE}/api/logs/analysis/solution/${fingerprint}/resolved`)
}

export function getTopSolutions(limit?: number): Promise<R<any[]>> {
  return get(`${LOG_BASE}/api/logs/analysis/solution/top`, { limit: limit || 10 })
}
