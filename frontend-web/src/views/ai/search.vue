<template>
  <div class="app-container">
    <el-card shadow="never">
      <div class="search-box">
        <!-- 检索模式按钮组（BM25 关键字、向量查询、RAG、混合、联网） -->
        <div class="search-mode-bar">
          <el-button
            v-for="mode in searchModes"
            :key="mode.type"
            :type="searchType === mode.type ? 'primary' : 'default'"
            :icon="mode.icon"
            :title="mode.desc"
            @click="searchType = mode.type"
          >
            {{ mode.label }}
          </el-button>
        </div>
        <el-input
          v-model="query"
          placeholder="输入搜索内容..."
          size="large"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" :loading="loading" @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
        <div class="search-mode-desc">{{ currentModeDesc }}</div>
      </div>

      <!-- 知识库选择（向量查询 / RAG 支持指定知识库；BM25 关键字为全库检索） -->
      <div v-if="['SIMILARITY','RAG'].includes(searchType)" class="rag-kb-select">
        <span style="margin-right:8px;font-size:14px;color:#606266">知识库:</span>
        <el-select v-model="ragKbName" placeholder="全部知识库" clearable size="small" style="width:200px">
          <el-option v-for="kb in knowledgeBases" :key="kb.name" :label="`${kb.name} (${kb.docCount})`" :value="kb.name" />
        </el-select>
        <el-button size="small" @click="loadKnowledgeBases" :loading="kbLoading" :icon="Refresh" style="margin-left:8px" />
      </div>

      <!-- 搜索建议 -->
      <div v-if="suggestions.length" class="suggestions">
        <span class="suggestion-label">相关建议：</span>
        <el-tag
          v-for="s in suggestions"
          :key="s"
          class="suggestion-tag"
          effect="plain"
          @click="query = s; handleSearch()"
        >{{ s }}</el-tag>
      </div>

      <!-- RAG 回答 -->
      <div v-if="ragAnswer" class="rag-answer">
        <div class="result-meta">RAG 智能回答 · 引用 {{ ragContextCount }} 条上下文 · 耗时 {{ latency }}ms</div>
        <el-card shadow="never" class="answer-card">
          <div class="answer-text">{{ ragAnswer }}</div>
        </el-card>
        <!-- 引用来源 -->
        <div v-if="ragContexts.length" class="context-section">
          <h4>参考来源</h4>
          <el-card v-for="(ctx, idx) in ragContexts" :key="idx" class="context-card" shadow="hover">
            <div class="context-header">
              <span class="context-title">{{ ctx.metadata?.title || `片段 #${idx + 1}` }}</span>
              <el-tag size="small" type="success">相关度: {{ (ctx.score * 100).toFixed(1) }}%</el-tag>
            </div>
            <p class="context-text">{{ ctx.text }}</p>
          </el-card>
        </div>
      </div>

      <!-- 普通搜索结果 -->
      <div v-if="results.length" class="results">
        <div class="result-meta">
          找到 {{ results.length }} 条结果 · 耗时 {{ latency }}ms · 类型: {{ searchType }}
        </div>
        <el-card
          v-for="(item, idx) in results"
          :key="idx"
          class="result-card"
          shadow="hover"
        >
          <div class="result-header">
            <span class="result-title" v-html="item.highlight || item.title || '未命名结果'"></span>
            <el-tag v-if="item.score" size="small" type="success">
              相关度: {{ searchType === 'BM25' ? item.score.toFixed(2) : (item.score * 100).toFixed(1) + '%' }}
            </el-tag>
          </div>
          <p class="result-content" v-html="item.highlight || item.content || ''"></p>
          <el-link v-if="item.source" type="primary" :href="item.source" target="_blank">{{ item.source }}</el-link>
        </el-card>
      </div>

      <!-- 混合搜索 -->
      <div v-if="hybridData" class="hybrid-results">
        <div class="result-meta">混合搜索 · 耗时 {{ hybridData.elapsedMs || latency }}ms</div>
        <el-card v-if="hybridData.answer" shadow="never" class="answer-card">
          <div class="answer-text">{{ hybridData.answer }}</div>
        </el-card>
        <el-tabs v-if="hybridData.localResults?.length || hybridData.webResults?.length">
          <el-tab-pane v-if="hybridData.localResults?.length" :label="`本地 (${hybridData.localResults.length})`">
            <div v-for="(item, idx) in hybridData.localResults" :key="'l'+idx" class="sub-result">
              <strong>{{ item.title }}</strong>
              <p v-html="item.highlight || item.content"></p>
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="hybridData.webResults?.length" :label="`联网 (${hybridData.webResults.length})`">
            <div v-for="(item, idx) in hybridData.webResults" :key="'w'+idx" class="sub-result">
              <strong>{{ item.title }}</strong>
              <p>{{ item.snippet }}</p>
              <el-link v-if="item.url" :href="item.url" target="_blank" type="primary">{{ item.url }}</el-link>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <el-empty v-else-if="!loading && searched && !ragAnswer && !hybridData" description="未找到相关结果" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import {
  unifiedSearch, searchSuggest, webSearch, ragSearch, hybridSearch,
  bm25Search, similaritySearch, getKnowledgeBases
} from '@/api/ai'
import type { KnowledgeBase } from '@/types'

const query = ref('')
const searchType = ref('BM25')

// 检索模式按钮配置（维护按钮/菜单信息：类型、标签、描述、图标）
interface SearchMode {
  type: string
  label: string
  desc: string
  icon: any
}
const searchModes: SearchMode[] = [
  { type: 'BM25', label: 'BM25 关键字', desc: '基于 Lucene 倒排索引的关键字检索，适合精确词匹配、术语查找', icon: 'Search' },
  { type: 'SIMILARITY', label: '向量查询', desc: '基于语义向量的相似度检索，能理解语义与近义词', icon: 'Share' },
  { type: 'RAG', label: 'RAG 智能问答', desc: '向量召回 + 大模型生成答案', icon: 'ChatDotRound' },
  { type: 'HYBRID', label: '混合检索', desc: '向量检索 + 关键字检索融合重排', icon: 'Operation' },
  { type: 'INTERNET', label: '联网搜索', desc: '调用外部搜索引擎检索实时信息', icon: 'Promotion' }
]
const currentModeDesc = computed(() => searchModes.find(m => m.type === searchType.value)?.desc || '')
const loading = ref(false)
const searched = ref(false)
const results = ref<any[]>([])
const suggestions = ref<string[]>([])
const latency = ref(0)

// RAG
const ragAnswer = ref('')
const ragContexts = ref<any[]>([])
const ragContextCount = ref(0)
const ragKbName = ref('')
const knowledgeBases = ref<KnowledgeBase[]>([])
const kbLoading = ref(false)

// 混合搜索
const hybridData = ref<any>(null)

async function loadKnowledgeBases() {
  kbLoading.value = true
  try {
    const res = await getKnowledgeBases()
    knowledgeBases.value = res?.data || []
  } catch { /* */ }
  finally { kbLoading.value = false }
}

async function handleSearch() {
  if (!query.value.trim()) return
  loading.value = true
  searched.value = true
  results.value = []
  suggestions.value = []
  ragAnswer.value = ''
  ragContexts.value = []
  ragContextCount.value = 0
  hybridData.value = null

  try {
    const start = Date.now()
    let res: any

    switch (searchType.value) {
      case 'BM25':
        res = await bm25Search(query.value, 10)
        break
      case 'SIMILARITY':
        res = await similaritySearch(query.value, 10, ragKbName.value || undefined)
        break
      case 'INTERNET':
        res = await webSearch(query.value)
        break
      case 'RAG': {
        res = await ragSearch(query.value, 5, ragKbName.value || undefined)
        break
      }
      case 'HYBRID':
        res = await hybridSearch(query.value)
        break
      default:
        res = await unifiedSearch({ query: query.value, searchType: searchType.value, maxResults: 10 })
    }

    latency.value = Date.now() - start
    const data = res?.data
    if (!data) return

    // 解析不同类型的搜索结果
    if (searchType.value === 'RAG') {
      // RAG: 显示 answer + context
      ragAnswer.value = data.answer || ''
      ragContexts.value = data.context || []
      ragContextCount.value = data.contextCount || ragContexts.value.length
    } else if (searchType.value === 'HYBRID') {
      // 混合搜索: 显示融合结果
      hybridData.value = data
    } else {
      // 本地/联网/向量: 显示 results 列表
      if (Array.isArray(data)) {
        results.value = data
      } else if (data.results) {
        results.value = data.results
      } else if (data.hits) {
        results.value = data.hits
      } else if (data.context) {
        // 向量查询返回 context 数组（text / score / metadata）
        results.value = data.context.map((c: any) => ({
          title: c.metadata?.title || '片段',
          content: c.text,
          highlight: c.text,
          score: c.score,
          source: c.metadata?.kbName || c.metadata?.source || ''
        }))
      } else {
        // 尝试将整个data作为单个结果
        results.value = data.query ? [data] : []
      }
    }

    // 获取搜索建议（BM25 关键字检索时才有意义）
    if (searchType.value === 'BM25' && results.value.length) {
      try {
        const sugRes = await searchSuggest(query.value)
        suggestions.value = sugRes?.data || []
      } catch { /* */ }
    }
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped lang="scss">
.search-box { max-width: 800px; margin: 0 auto; }

.search-mode-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.search-mode-desc {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rag-kb-select {
  max-width: 800px;
  margin: 10px auto 0;
  display: flex;
  align-items: center;
}

.suggestions {
  max-width: 800px;
  margin: 12px auto;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.suggestion-label { font-size: 13px; color: #909399; }
.suggestion-tag { cursor: pointer; }

.results { max-width: 800px; margin: 16px auto 0; }
.hybrid-results { max-width: 800px; margin: 16px auto 0; }

.result-meta { font-size: 13px; color: #909399; margin-bottom: 12px; }
.result-card { margin-bottom: 12px; }

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-title { font-size: 16px; font-weight: 600; color: #303133; }
.result-content { color: #606266; line-height: 1.6; margin-bottom: 8px; }

// RAG
.rag-answer { max-width: 800px; margin: 16px auto 0; }
.answer-card { background: #f0f9eb; border-color: #c6e5b7; margin-bottom: 16px; }
.answer-text { font-size: 15px; line-height: 1.8; color: #303133; white-space: pre-wrap; }

.context-section {
  h4 { color: #303133; margin-bottom: 10px; font-size: 15px; }
}

.context-card { margin-bottom: 10px; }
.context-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.context-title { font-weight: 600; font-size: 14px; color: #303133; }
.context-text { font-size: 13px; color: #606266; line-height: 1.6; }

.sub-result {
  padding: 8px 0;
  p { color: #606266; font-size: 13px; margin: 4px 0; }
  strong { font-size: 14px; }
}
</style>
