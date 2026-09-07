import { get, post } from '@/utils/request.js'

const AI_BASE = '/ai-agent-server'

export function localSearch(query, n) {
  return get(`${AI_BASE}/api/ai/search/local`, { q: query, n: n || 10 })
}

export function webSearch(query, n) {
  return get(`${AI_BASE}/api/ai/search/web`, { q: query, n: n || 10 })
}

export function ragSearch(query, k) {
  return get(`${AI_BASE}/api/ai/search/rag`, { q: query, k: k || 5 })
}

export function hybridSearch(query, n) {
  return get(`${AI_BASE}/api/ai/search/hybrid`, { q: query, n: n || 10 })
}

export function getKnowledgeBases() {
  return get(`${AI_BASE}/api/ai/knowledge/kb`)
}

export function getKnowledgeDocs(kbName) {
  return get(`${AI_BASE}/api/ai/knowledge/kb/${kbName}/docs`)
}

export function getAlerts() {
  return get(`${AI_BASE}/api/ai/analysis/alerts`)
}

export function getAlertSummary() {
  return get(`${AI_BASE}/api/ai/analysis/alerts/summary`)
}
