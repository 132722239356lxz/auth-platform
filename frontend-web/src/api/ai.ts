import { get, post, put, del } from '@/utils/request'
import { getAccessToken } from '@/utils/auth'
import type {
  R, KnowledgeDocRequest, KnowledgeDoc, KnowledgeBase, KnowledgeDocDetail, UploadTask, UploadResult,
  SearchRequest, SearchResult, AlertVO, AlertPageResult, AlertQueryParams,
  ChatAttachment, ChatResponseData, ChatSessionVO, SessionHistoryData, CacheStatsVO
} from '@/types'

const AI_BASE = '/ai-agent-server'

// ==================== 数据分析预警 ====================

export function triggerAnalysis(): Promise<R<any>> {
  return post(`${AI_BASE}/api/ai/analysis/trigger`)
}

export function queryAnalysis(query: string): Promise<R<any>> {
  return post(`${AI_BASE}/api/ai/analysis/query`, { query })
}

export function getMetrics(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/analysis/metrics`)
}

export function getAlerts(params?: AlertQueryParams): Promise<R<AlertPageResult>> {
  return get(`${AI_BASE}/api/ai/analysis/alerts/list`, params)
}

export function getAlertsByLevel(level: string): Promise<R<AlertVO[]>> {
  return get(`${AI_BASE}/api/ai/analysis/alerts/level/${level}`)
}

export function getRecentAlerts(n?: number): Promise<R<AlertVO[]>> {
  return get(`${AI_BASE}/api/ai/analysis/alerts/recent`, { n: n || 20 })
}

export function getAlertsSummary(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/analysis/alerts/summary`)
}

export function resolveAlert(id: number, by: string): Promise<R<void>> {
  return put(`${AI_BASE}/api/ai/analysis/alerts/${id}/resolve`, null, { params: { by } })
}

export function markAlertRead(id: number): Promise<R<void>> {
  return put(`${AI_BASE}/api/ai/analysis/alerts/${id}/read`)
}

// ==================== 健康检查 ====================

export function checkLLMHealth(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/health/llm`)
}

export function checkServiceStatus(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/health/status`)
}

// ==================== 知识库管理 ====================

export function createKnowledgeBase(name: string, description?: string): Promise<R<KnowledgeBase>> {
  return post(`${AI_BASE}/api/ai/knowledge/base`, { name, description })
}

export function updateKnowledgeBase(id: number, name: string, description?: string): Promise<R<KnowledgeBase>> {
  return put(`${AI_BASE}/api/ai/knowledge/base/${id}`, { name, description })
}

export function deleteKnowledgeBaseById(id: number): Promise<R<void>> {
  return del(`${AI_BASE}/api/ai/knowledge/base/${id}`)
}

export function getKnowledgeBases(): Promise<R<KnowledgeBase[]>> {
  return get(`${AI_BASE}/api/ai/knowledge/base`)
}

export function getKnowledgeBaseById(id: number): Promise<R<KnowledgeBase>> {
  return get(`${AI_BASE}/api/ai/knowledge/base/${id}`)
}

export function addKnowledgeDoc(data: KnowledgeDocRequest): Promise<R<KnowledgeDoc>> {
  return post(`${AI_BASE}/api/ai/knowledge/doc`, data)
}

export function updateKnowledgeDoc(id: number, data: KnowledgeDocRequest): Promise<R<KnowledgeDoc>> {
  return put(`${AI_BASE}/api/ai/knowledge/doc/${id}`, data)
}

/** 上传文件到知识库（单文件 + 进度回调 + 切片策略） */
export function uploadKnowledgeFile(
  file: File,
  kbName: string,
  options?: {
    title?: string
    contentType?: string
    splitterType?: string
    chunkSize?: number
    overlap?: number
    onProgress?: (percent: number, loaded: number, total: number) => void
  }
): Promise<R<UploadResult[]>> {
  const formData = new FormData()
  formData.append('files', file, file.name)
  formData.append('kbName', kbName)
  if (options?.title) formData.append('title', options.title)
  if (options?.contentType) formData.append('contentType', options.contentType)
  if (options?.splitterType) formData.append('splitterType', options.splitterType)
  if (options?.chunkSize) formData.append('chunkSize', String(options.chunkSize))
  if (options?.overlap) formData.append('overlap', String(options.overlap))
  return post(`${AI_BASE}/api/ai/knowledge/upload`, formData, {
    onUploadProgress: options?.onProgress
      ? (e: any) => options.onProgress!(Math.round((e.loaded * 100) / (e.total || 1)), e.loaded, e.total || 1)
      : undefined
  })
}

/** 获取上传任务状态 */
export function getUploadTask(taskId: string): Promise<R<UploadTask>> {
  return get(`${AI_BASE}/api/ai/knowledge/task/${taskId}`)
}

/** 获取支持的上传文件格式 */
export function getSupportedFormats(): Promise<R<string[]>> {
  return get(`${AI_BASE}/api/ai/knowledge/supported-formats`)
}

export function getKnowledgeDocsByKB(kbName: string): Promise<R<KnowledgeDoc[]>> {
  return get(`${AI_BASE}/api/ai/knowledge/kb/${encodeURIComponent(kbName)}/docs`)
}

export function getAllKnowledgeDocs(): Promise<R<KnowledgeDoc[]>> {
  return get(`${AI_BASE}/api/ai/knowledge/docs`)
}

export function getKnowledgeDocById(id: number): Promise<R<KnowledgeDocDetail>> {
  return get(`${AI_BASE}/api/ai/knowledge/doc/${id}`)
}

export function getKnowledgeDocChunks(id: number): Promise<R<KnowledgeDocDetail>> {
  return get(`${AI_BASE}/api/ai/knowledge/doc/${id}/chunks`)
}

export function deleteKnowledgeDoc(id: number): Promise<R<void>> {
  return del(`${AI_BASE}/api/ai/knowledge/doc/${id}`)
}

export function deleteKnowledgeBase(kbName: string): Promise<R<void>> {
  return del(`${AI_BASE}/api/ai/knowledge/kb/${encodeURIComponent(kbName)}`)
}

export function getKnowledgeStats(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/knowledge/stats`)
}

// ==================== 智能搜索 ====================

export function unifiedSearch(data: SearchRequest): Promise<R<SearchResult>> {
  return post(`${AI_BASE}/api/ai/search`, data)
}

export function localSearch(q: string): Promise<R<SearchResult>> {
  return get(`${AI_BASE}/api/ai/search/local`, { q })
}

/** BM25 全文检索（基于 Lucene，对应 /local 端点）。后端该端点为全库检索，不接收 kbName */
export function bm25Search(q: string, topK?: number): Promise<R<SearchResult>> {
  return get(`${AI_BASE}/api/ai/search/local`, { q, n: topK || 5 })
}

export function searchSuggest(q: string): Promise<R<string[]>> {
  return get(`${AI_BASE}/api/ai/search/suggest`, { q })
}

export function webSearch(q: string): Promise<R<SearchResult>> {
  return get(`${AI_BASE}/api/ai/search/web`, { q })
}

export function similaritySearch(q: string, k?: number, kbName?: string): Promise<R<SearchResult>> {
  return get(`${AI_BASE}/api/ai/search/similarity`, { q, k: k || 5, kbName })
}

export function similarityLocalSearch(q: string, k?: number, kbName?: string): Promise<R<SearchResult>> {
  return get(`${AI_BASE}/api/ai/search/similarity-local`, { q, k: k || 5, kbName })
}

export function ragSearch(q: string, k?: number, kbName?: string): Promise<R<SearchResult>> {
  if (kbName) {
    return unifiedSearch({ query: q, searchType: 'RAG', kbName, maxResults: k || 5 })
  }
  return get(`${AI_BASE}/api/ai/search/rag`, { q, k: k || 5 })
}

export function hybridSearch(q: string): Promise<R<SearchResult>> {
  return get(`${AI_BASE}/api/ai/search/hybrid`, { q })
}

export function getIndexStats(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/search/index-stats`)
}

// ==================== AI 智能对话Agent ====================

export interface ChatRequest {
  sessionId?: string
  question: string
  useMemory?: boolean
  /** 是否开启思考模式：模型先展示推理过程，再给出最终回答 */
  thinking?: boolean
  attachments?: ChatAttachment[]
  userId?: string
}

/**
 * 多模态智能对话：支持文本 + 图片/文件附件
 * 附件会作为 multipart/form-data 上传，后端进行视觉分析或文档解析
 */
export function chatAsk(data: ChatRequest): Promise<R<ChatResponseData>> {
  const formData = new FormData()
  formData.append('sessionId', data.sessionId || '')
  formData.append('question', data.question)
  formData.append('useMemory', String(data.useMemory !== false))
  formData.append('thinking', String(data.thinking !== false))
  if (data.userId) {
    formData.append('userId', data.userId)
  }
  data.attachments?.forEach((att) => {
    if (att.raw) {
      formData.append(`files`, att.raw, att.name)
    } else if (att.url && att.type === 'image' && att.url.startsWith('data:')) {
      formData.append(`base64Images`, att.url)
    }
  })
  return post(`${AI_BASE}/api/ai/chat/ask`, formData)
}

export function quickAsk(q: string): Promise<R<ChatResponseData>> {
  return get(`${AI_BASE}/api/ai/chat/quick`, { q })
}

/**
 * 流式多模态对话：通过 SSE (Server-Sent Events) 实时返回 AI 生成内容
 * 附件以 multipart/form-data 上传，后端逐个 token 推送
 */
export async function chatStream(data: ChatRequest): Promise<ReadableStream> {
  const token = getAccessToken()
  const headers: HeadersInit = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  // 区分文件附件（二进制）和 base64/纯文本：
  // - 无文件附件时走 JSON，显式 Content-Type，避免 multipart 在某些代理下 415
  // - 有文件附件时必须走 multipart/form-data
  const fileAttachments = data.attachments?.filter((att) => att.raw) || []
  const base64Images = data.attachments
    ?.filter((att) => att.url && att.type === 'image' && att.url.startsWith('data:'))
    .map((att) => att.url as string)

  function buildMultipartBody(): FormData {
    const formData = new FormData()
    formData.append('sessionId', data.sessionId || '')
    formData.append('question', data.question)
    formData.append('useMemory', String(data.useMemory !== false))
    formData.append('thinking', String(data.thinking !== false))
    if (data.userId) {
      formData.append('userId', data.userId)
    }
    fileAttachments.forEach((att) => {
      formData.append('files', att.raw as Blob, att.name)
    })
    base64Images?.forEach((url) => {
      formData.append('base64Images', url)
    })
    return formData
  }

  async function doFetch(body: BodyInit, contentType?: string): Promise<Response> {
    const reqHeaders: HeadersInit = {}
    if (token) {
      reqHeaders.Authorization = `Bearer ${token}`
    }
    if (contentType) {
      reqHeaders['Content-Type'] = contentType
    }
    return fetch(`${AI_BASE}/api/ai/chat/stream`, {
      method: 'POST',
      headers: reqHeaders,
      body
    })
  }

  let res: Response
  if (fileAttachments.length === 0) {
    // 优先走 JSON，显式 Content-Type，避免 multipart 在代理下丢失导致 415
    const jsonBody = JSON.stringify({
      sessionId: data.sessionId || '',
      question: data.question,
      useMemory: data.useMemory !== false,
      thinking: data.thinking !== false,
      userId: data.userId,
      base64Images: base64Images && base64Images.length > 0 ? base64Images : undefined
    })
    res = await doFetch(jsonBody, 'application/json')

    // 兼容旧后端：若只接受 multipart，收到 415 后回退到 FormData
    if (res.status === 415) {
      res = await doFetch(buildMultipartBody())
    }
  } else {
    // 有文件附件时必须走 multipart
    res = await doFetch(buildMultipartBody())
  }

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  return res.body!
}

export function clearSession(sessionId: string): Promise<R<void>> {
  return del(`${AI_BASE}/api/ai/chat/session/${sessionId}`)
}

export function chatStatus(): Promise<R<any>> {
  return get(`${AI_BASE}/api/ai/chat/status`)
}

// ==================== 会话管理 (V3.0 新增) ====================

/** 创建新会话 */
export function createSession(userId: string, title?: string): Promise<R<ChatSessionVO>> {
  return post(`${AI_BASE}/api/ai/chat/session`, null, { params: { userId, title } })
}

/** 获取用户的所有会话列表 */
export function listSessions(userId: string): Promise<R<ChatSessionVO[]>> {
  return get(`${AI_BASE}/api/ai/chat/sessions`, { userId })
}

/** 切换到指定会话 */
export function switchSession(sessionId: string): Promise<R<ChatSessionVO>> {
  return post(`${AI_BASE}/api/ai/chat/session/${sessionId}/switch`)
}

/** 加载会话完整历史（含压缩摘要） */
export function loadSessionHistory(sessionId: string): Promise<R<SessionHistoryData>> {
  return get(`${AI_BASE}/api/ai/chat/session/${sessionId}/history`)
}

// ==================== 消息管理 (V3.0 新增) ====================

/** 删除会话中的单条消息 */
export function deleteMessage(sessionId: string, index: number): Promise<R<void>> {
  return del(`${AI_BASE}/api/ai/chat/session/${sessionId}/message/${index}`)
}

// ==================== 缓存统计 (V3.0 新增) ====================

/** 获取响应缓存统计 */
export function getCacheStats(): Promise<R<CacheStatsVO>> {
  return get(`${AI_BASE}/api/ai/chat/cache/stats`)
}
