<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 搜索栏 -->
      <el-col :span="24">
        <el-card shadow="never">
          <el-input
            v-model="fingerprint"
            placeholder="输入错误指纹 (errorFingerprint)"
            style="width: 400px"
            clearable
            @keyup.enter="analyzeFingerprint"
          >
            <template #append>
              <el-button :loading="analyzing" @click="analyzeFingerprint">AI分析</el-button>
            </template>
          </el-input>
          <el-divider direction="vertical" />
          <el-input
            v-model="traceId"
            placeholder="或输入 TraceId"
            style="width: 300px"
            clearable
            @keyup.enter="analyzeTrace"
          >
            <template #append>
              <el-button :loading="analyzing" @click="analyzeTrace">Trace分析</el-button>
            </template>
          </el-input>
        </el-card>
      </el-col>

      <!-- 分析结果 -->
      <el-col :span="24" v-if="analysisResult" style="margin-top: 16px">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>AI 分析结果</span></div></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="错误指纹">{{ analysisResult.errorFingerprint }}</el-descriptions-item>
            <el-descriptions-item label="根因分析">{{ analysisResult.rootCause || '分析中...' }}</el-descriptions-item>
            <el-descriptions-item label="解决方案">{{ analysisResult.solution || '暂无方案' }}</el-descriptions-item>
            <el-descriptions-item v-if="analysisResult.steps" label="修复步骤">
              <pre style="white-space: pre-wrap">{{ analysisResult.steps }}</pre>
            </el-descriptions-item>
            <el-descriptions-item label="出现次数">{{ analysisResult.occurrenceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ analysisResult.status || 'DRAFT' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 热门解决方案 -->
      <el-col :span="24" style="margin-top: 16px">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>热门解决方案</span></div></template>
          <el-table :data="topSolutions" border stripe size="small">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="errorFingerprint" label="错误指纹" min-width="200" />
            <el-table-column prop="rootCause" label="根因" min-width="200" show-overflow-tooltip />
            <el-table-column prop="resolveCount" label="解决次数" width="90" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" @click="fingerprint = row.errorFingerprint; analyzeFingerprint()">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 相似错误搜索 -->
      <el-col :span="24" style="margin-top: 16px">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>搜索相似错误</span></div></template>
          <el-input v-model="searchMessage" placeholder="输入错误消息关键词" style="width: 400px" @keyup.enter="searchSimilar">
            <template #append>
              <el-button :loading="searching" @click="searchSimilar">搜索</el-button>
            </template>
          </el-input>
          <el-table v-if="similarErrors.length" :data="similarErrors" border stripe size="small" style="margin-top: 12px">
            <el-table-column prop="logTime" label="时间" width="170" />
            <el-table-column prop="level" label="级别" width="80" />
            <el-table-column prop="message" label="消息" min-width="300" show-overflow-tooltip />
            <el-table-column prop="module" label="模块" width="130" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { analyzeByFingerprint, analyzeByTraceId, getTopSolutions, searchSimilarErrors } from '@/api/log'

const route = useRoute()
const fingerprint = ref('')
const traceId = ref('')
const searchMessage = ref('')
const analyzing = ref(false)
const searching = ref(false)
const analysisResult = ref<any>(null)
const topSolutions = ref<any[]>([])
const similarErrors = ref<any[]>([])

async function analyzeFingerprint() {
  if (!fingerprint.value) return
  analyzing.value = true
  analysisResult.value = null
  try {
    const res = await analyzeByFingerprint(fingerprint.value)
    analysisResult.value = res?.data || null
  } catch { /* */ }
  finally { analyzing.value = false }
}

async function analyzeTrace() {
  if (!traceId.value) return
  analyzing.value = true
  analysisResult.value = null
  try {
    const res = await analyzeByTraceId(traceId.value)
    analysisResult.value = res?.data || null
  } catch { /* */ }
  finally { analyzing.value = false }
}

async function loadTopSolutions() {
  try {
    const res = await getTopSolutions(10)
    topSolutions.value = res?.data || []
  } catch { topSolutions.value = [] }
}

async function searchSimilar() {
  if (!searchMessage.value) return
  searching.value = true
  try {
    const res = await searchSimilarErrors(searchMessage.value)
    similarErrors.value = res?.data || []
  } catch { similarErrors.value = [] }
  finally { searching.value = false }
}

onMounted(() => {
  const fp = route.query.fingerprint as string
  if (fp) {
    fingerprint.value = fp
    analyzeFingerprint()
  }
  loadTopSolutions()
})
</script>

<style scoped>
.card-header { font-weight: 600; }
</style>
