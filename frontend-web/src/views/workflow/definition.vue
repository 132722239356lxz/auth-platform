<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="query.keyword"
          placeholder="关键字搜索"
          clearable
          style="width: 220px"
          @keyup.enter="loadDefinitions"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadDefinitions">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <!-- 左侧：流程定义列表 -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="def-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">流程定义</span>
              <el-button v-permission="'workflow:definition:add'" type="primary" size="small" @click="handleAdd">
                新增
              </el-button>
            </div>
          </template>
          <el-table
            :data="filteredDefinitions"
            v-loading="defLoading"
            border
            stripe
            highlight-current-row
            style="width: 100%"
            @row-click="handleDefRowClick"
          >
            <el-table-column prop="definitionKey" label="定义标识" min-width="110" show-overflow-tooltip />
            <el-table-column prop="definitionName" label="名称" min-width="100" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="categoryTagType(row.category)">{{ row.category }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="60" align="center" />
            <el-table-column prop="status" label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-permission="'workflow:definition:edit'" size="small" @click.stop="handleEdit(row)">
                  编辑
                </el-button>
                <el-button
                  v-permission="'workflow:definition:edit'"
                  size="small"
                  :type="row.status === 1 ? 'warning' : 'success'"
                  @click.stop="handleToggleStatus(row)"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button
                  v-permission="'workflow:definition:delete'"
                  size="small"
                  type="danger"
                  @click.stop="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：节点设计器 -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="node-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                流程节点
                <span v-if="selectedDef" style="font-weight: 400; color: #409eff">
                  - {{ selectedDef.definitionName }}
                </span>
              </span>
              <el-button
                type="success"
                size="small"
                :disabled="!selectedDef"
                @click="openPreview"
              >
                预览流程图
              </el-button>
              <el-button
                v-permission="'workflow:definition:edit'"
                type="primary"
                size="small"
                :disabled="!selectedDef"
                @click="handleAddNode"
              >
                添加节点
              </el-button>
            </div>
          </template>

          <div v-if="!selectedDef" class="empty-hint">请从左侧选择一个流程定义</div>

          <div v-else v-loading="nodeLoading" class="node-list">
            <el-empty v-if="nodes.length === 0" description="暂无节点，请添加" />
            <div v-for="(node, idx) in nodes" :key="idx" class="node-card-item">
              <div class="node-header">
                <el-icon class="drag-handle"><Rank /></el-icon>
                <span class="node-index">节点 {{ idx + 1 }}</span>
                <div class="node-actions">
                  <el-button
                    size="small"
                    circle
                    :disabled="idx === 0"
                    title="上移"
                    @click="handleMoveUp(idx)"
                  >
                    <el-icon><ArrowUp /></el-icon>
                  </el-button>
                  <el-button
                    size="small"
                    circle
                    :disabled="idx === nodes.length - 1"
                    title="下移"
                    @click="handleMoveDown(idx)"
                  >
                    <el-icon><ArrowDown /></el-icon>
                  </el-button>
                  <el-button
                    v-permission="'workflow:definition:delete'"
                    type="danger"
                    size="small"
                    circle
                    title="删除"
                    @click="handleDeleteNode(idx)"
                  >
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </div>

              <el-form :model="node" label-width="100px" size="default">
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="节点名称">
                      <el-input v-model="node.nodeName" placeholder="请输入节点名称" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="节点类型">
                      <el-select v-model="node.nodeType" placeholder="请选择节点类型" style="width: 100%">
                        <el-option label="开始" value="START" />
                        <el-option label="审批" value="APPROVAL" />
                        <el-option label="条件" value="CONDITION" />
                        <el-option label="并行开始" value="PARALLEL_START" />
                        <el-option label="并行结束" value="PARALLEL_END" />
                        <el-option label="回调通知" value="CALLBACK" />
                        <el-option label="结束" value="END" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <!-- 审批节点专属配置 -->
                <template v-if="node.nodeType === 'APPROVAL'">
                  <el-row :gutter="12">
                    <el-col :span="12">
                      <el-form-item label="审批人">
                        <el-input v-model="node.approvers" placeholder="多人用逗号分隔" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="审批策略">
                        <el-select v-model="node.approverStrategy" placeholder="请选择" style="width: 100%">
                          <el-option label="指定人员" value="SPECIFIC" />
                          <el-option label="按角色" value="ROLE_BASED" />
                          <el-option label="部门领导" value="DEPARTMENT_LEADER" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="会签">
                        <el-switch v-model="node.countersign" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="驳回策略">
                        <el-select v-model="node.rejectStrategy" placeholder="请选择" style="width: 100%">
                          <el-option label="退回上一节点" value="TO_PREV" />
                          <el-option label="退回发起人" value="TO_START" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="超时(小时)">
                        <el-input-number v-model="node.timeoutHours" :min="0" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </template>

                <!-- 条件节点专属配置 -->
                <template v-if="node.nodeType === 'CONDITION'">
                  <el-row :gutter="12">
                    <el-col :span="12">
                      <el-form-item label="条件表达式">
                        <el-input v-model="node.conditionExpression" placeholder="如 amount > 5000" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="条件失败策略">
                        <el-select v-model="node.onConditionFail" placeholder="请选择" style="width: 100%">
                          <el-option label="跳过" value="SKIP" />
                          <el-option label="拒绝" value="REJECT" />
                          <el-option label="转人工" value="MANUAL" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </template>

                <el-row :gutter="12">
                  <el-col :span="8">
                    <el-form-item label="排序">
                      <el-input-number v-model="node.sortOrder" :min="0" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="执行模式">
                      <el-select v-model="node.execMode" placeholder="串行" style="width: 100%" clearable>
                        <el-option label="串行执行" value="SERIAL" />
                        <el-option label="并行执行" value="PARALLEL" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="8" v-if="node.execMode === 'PARALLEL'">
                    <el-form-item label="并行组">
                      <el-input v-model="node.parallelGroup" placeholder="组标识" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="12" v-if="node.execMode === 'PARALLEL' && node.nodeType !== 'PARALLEL_START'">
                  <el-col :span="12">
                    <el-form-item label="所属并行组">
                      <el-select v-model="node.parentNodeId" placeholder="选择 PARALLEL_START 节点" style="width: 100%" clearable>
                        <el-option
                          v-for="startNode in parallelStartNodes(nodes, node)"
                          :key="startNode.id"
                          :label="startNode.nodeName || `并行开始-${startNode.id}`"
                          :value="startNode.id!"
                        />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item>
                      <template #label>
                        <span />
                      </template>
                      <el-text type="info" size="small">并行审批节点需挂到“并行开始”下</el-text>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>

            <!-- 保存节点按钮 -->
            <div v-if="selectedDef && nodes.length > 0" class="save-nodes-bar">
              <el-button type="primary" :loading="nodeSaving" @click="handleSaveNodes">
                保存节点配置
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 流程图预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      title="流程图预览"
      width="900px"
      destroy-on-close
      @closed="closePreview"
    >
      <div class="preview-toolbar">
        <el-button type="primary" size="small" @click="exportFlowImage">
          <el-icon><Download /></el-icon> 导出图片
        </el-button>
      </div>
      <div ref="chartRef" class="flow-chart" />
    </el-dialog>

    <!-- 新增/编辑流程定义弹窗 -->
    <el-dialog
      v-model="defDialogVisible"
      :title="isEditDef ? '编辑流程定义' : '新增流程定义'"
      width="520px"
    >
      <el-form ref="defFormRef" :model="defForm" :rules="defFormRules" label-width="100px">
        <el-form-item label="定义标识" prop="definitionKey">
          <el-input
            v-model="defForm.definitionKey"
            placeholder="如 permission_approval"
            :disabled="isEditDef"
          />
        </el-form-item>
        <el-form-item label="定义名称" prop="definitionName">
          <el-input v-model="defForm.definitionName" placeholder="请输入流程定义名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="defForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="defForm.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="权限审批" value="PERMISSION" />
            <el-option label="资源审批" value="RESOURCE" />
            <el-option label="角色审批" value="ROLE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="defSaving" @click="handleSaveDef">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Rank, Close, Download, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getWorkflowDefinitions,
  createWorkflowDefinition,
  getWorkflowDefinitionDetail,
  updateWorkflowDefinition,
  deleteWorkflowDefinition,
  toggleDefinitionStatus
} from '@/api/workflow'
import { useUserStore } from '@/stores/user'
import type { WorkflowDefinition, DefinitionDetail, DefinitionNode } from '@/types'

const userStore = useUserStore()

// ==================== 左侧：定义列表 ====================
const defLoading = ref(false)
const defList = ref<WorkflowDefinition[]>([])
const selectedDef = ref<WorkflowDefinition | null>(null)

const query = reactive({ keyword: '', status: undefined as number | undefined })

const filteredDefinitions = computed(() => {
  return defList.value.filter(d => {
    if (query.status !== undefined && d.status !== query.status) return false
    if (query.keyword) {
      const k = query.keyword.toLowerCase()
      return (
        d.definitionName.toLowerCase().includes(k) ||
        d.definitionKey.toLowerCase().includes(k) ||
        (d.category || '').toLowerCase().includes(k)
      )
    }
    return true
  })
})

function categoryTagType(cat: string | undefined): string {
  if (cat === 'PERMISSION') return 'primary'
  if (cat === 'RESOURCE') return 'success'
  if (cat === 'ROLE') return 'warning'
  return 'info'
}

async function loadDefinitions() {
  defLoading.value = true
  try {
    const res = await getWorkflowDefinitions()
    defList.value = res?.data || []
  } catch {
    defList.value = []
  } finally {
    defLoading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.status = undefined
  loadDefinitions()
}

function handleDefRowClick(row: WorkflowDefinition) {
  selectedDef.value = row
  loadNodes(row.id!)
}

// ==================== 右侧：节点设计器 ====================
const nodeLoading = ref(false)
const nodeSaving = ref(false)
const nodes = ref<DefinitionNode[]>([])

async function loadNodes(defId: number) {
  nodeLoading.value = true
  try {
    const res = await getWorkflowDefinitionDetail(defId)
    const detail = res?.data
    nodes.value = (detail?.nodes || []).map((n: any) => ({ ...n }))
  } catch {
    nodes.value = []
  } finally {
    nodeLoading.value = false
  }
}

function handleAddNode() {
  const newNode: any = {
    nodeName: '',
    nodeType: 'APPROVAL',
    execMode: 'SERIAL',
    parallelGroup: '',
    conditionExpression: '',
    onConditionFail: 'REJECT',
    approverStrategy: 'SPECIFIC',
    approvers: '',
    approverRole: '',
    sortOrder: nodes.value.length,
    timeoutHours: 0,
    countersign: false,
    rejectStrategy: 'TO_PREV'
  }
  nodes.value.push(newNode)
}

function handleDeleteNode(idx: number) {
  nodes.value.splice(idx, 1)
}

function swapNodes(a: number, b: number) {
  if (a < 0 || b < 0 || a >= nodes.value.length || b >= nodes.value.length) return
  const temp = nodes.value[a]
  nodes.value[a] = nodes.value[b]
  nodes.value[b] = temp
  // 同步 sortOrder 与数组顺序保持一致
  nodes.value.forEach((n, i) => {
    n.sortOrder = i
  })
}

function handleMoveUp(idx: number) {
  swapNodes(idx, idx - 1)
}

function handleMoveDown(idx: number) {
  swapNodes(idx, idx + 1)
}

function parallelStartNodes(allNodes: DefinitionNode[], currentNode: DefinitionNode) {
  return allNodes.filter(
    n => n.nodeType === 'PARALLEL_START' && n.id !== currentNode.id
  )
}

// ==================== 流程图预览 ====================
const previewVisible = ref(false)
const chartRef = ref<HTMLDivElement>()
let chartInstance: any = null

function openPreview() {
  if (!selectedDef.value || nodes.value.length === 0) {
    ElMessage.warning('请先选择包含节点的流程定义')
    return
  }
  previewVisible.value = true
  nextTick(() => {
    if (chartRef.value) {
      chartInstance?.dispose()
      chartInstance = echarts.init(chartRef.value)
      chartInstance.setOption(buildFlowChartOption(nodes.value))
    }
  })
}

function closePreview() {
  chartInstance?.dispose()
  chartInstance = null
}

function exportFlowImage() {
  if (!chartInstance || !selectedDef.value) return
  const url = chartInstance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' })
  const a = document.createElement('a')
  a.href = url
  a.download = `flow-${selectedDef.value.definitionKey}-${Date.now()}.png`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

function buildFlowChartOption(nodes: DefinitionNode[]) {
  const sorted = [...nodes].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  const baseY = 250
  const laneGap = 140
  const stepX = 220
  let currentX = 100
  const positions = new Map<number, { x: number; y: number }>()

  const mainLinkStyle = { opacity: 0.9, width: 2, curveness: 0, color: '#909399' }
  const branchLinkStyle = { opacity: 0.95, width: 2, curveness: 0.12, color: '#9254de', type: 'dashed' as const }

  // 并行分支节点 id；PARALLEL_START / PARALLEL_END 为隐藏壳节点，不渲染
  const branchNodeIds = new Set<number>()
  const hiddenNodeIds = new Set<number>()

  // 收集并行组：PARALLEL_START 之后到 PARALLEL_END 之间的节点作为并行分支
  // 优先采用显式 parentNodeId，否则按顺序推断
  const parallelGroups: {
    children: DefinitionNode[]
    mergeNode: DefinitionNode | undefined
  }[] = []

  for (let i = 0; i < sorted.length; i++) {
    const node = sorted[i]
    if (node.nodeType !== 'PARALLEL_START') continue

    const explicitChildren = sorted.filter(
      n => n.parentNodeId && n.parentNodeId === node.id
    )

    let endNode: DefinitionNode | undefined
    let children: DefinitionNode[]

    if (explicitChildren.length > 0) {
      children = explicitChildren
      endNode = sorted.find(
        n => n.nodeType === 'PARALLEL_END' && (n.sortOrder || 0) > (node.sortOrder || 0)
      )
    } else {
      const endIdx = sorted.findIndex(
        (n, idx) => idx > i && n.nodeType === 'PARALLEL_END'
      )
      const sliceEnd = endIdx > 0 ? endIdx : sorted.length
      children = sorted
        .slice(i + 1, sliceEnd)
        .filter(n => n.nodeType !== 'PARALLEL_END')
      endNode = endIdx > 0 ? sorted[endIdx] : undefined
    }

    // 合并点：PARALLEL_END 之后的下一个可见节点
    const endIdx = endNode ? sorted.findIndex(n => n.id === endNode!.id) : -1
    const mergeNode = endIdx >= 0 && endIdx < sorted.length - 1 ? sorted[endIdx + 1] : undefined

    children.forEach(c => branchNodeIds.add(c.id))
    hiddenNodeIds.add(node.id)
    if (endNode) hiddenNodeIds.add(endNode.id)
    parallelGroups.push({ children, mergeNode })
  }

  // 构建展示段：普通节点 或 并行分支段
  const segments: Array<
    { type: 'node'; node: DefinitionNode } |
    { type: 'parallel'; prev: DefinitionNode | null; children: DefinitionNode[]; merge: DefinitionNode | undefined }
  > = []
  let lastNormalNode: DefinitionNode | null = null
  let i = 0
  while (i < sorted.length) {
    const node = sorted[i]
    if (hiddenNodeIds.has(node.id)) {
      i++
      continue
    }
    if (branchNodeIds.has(node.id)) {
      const grp = parallelGroups.find(g => g.children.some(c => c.id === node.id))
      if (grp) {
        segments.push({ type: 'parallel', prev: lastNormalNode, children: grp.children, merge: grp.mergeNode })
        const skipIds = new Set(grp.children.map(c => c.id))
        if (grp.mergeNode) skipIds.add(grp.mergeNode.id)
        while (i < sorted.length && (skipIds.has(sorted[i].id) || hiddenNodeIds.has(sorted[i].id))) {
          i++
        }
        continue
      }
    }
    segments.push({ type: 'node', node })
    lastNormalNode = node
    i++
  }

  // 按段布局
  segments.forEach(seg => {
    if (seg.type === 'node') {
      positions.set(seg.node.id, { x: currentX, y: baseY })
      currentX += stepX
      return
    }

    const prevX = seg.prev ? positions.get(seg.prev.id)!.x : currentX
    const branchX = prevX + 180
    const mergeX = branchX + 240
    const count = seg.children.length

    seg.children.forEach((child, idx) => {
      const offset = (idx - (count - 1) / 2) * laneGap
      positions.set(child.id, { x: branchX, y: baseY + offset })
    })

    if (seg.merge) {
      positions.set(seg.merge.id, { x: mergeX, y: baseY })
    }

    currentX = mergeX + stepX
  })

  // 连线：按段顺序连接
  const links: any[] = []
  for (let s = 0; s < segments.length - 1; s++) {
    const curSeg = segments[s]
    const nextSeg = segments[s + 1]
    const curTailId = getSegmentTailId(curSeg)
    const nextHeadId = getSegmentHeadId(nextSeg)
    if (curTailId && nextHeadId) {
      links.push({ source: String(curTailId), target: String(nextHeadId), lineStyle: { ...mainLinkStyle } })
    }
  }

  // 并行段内部连线：前驱 -> 各分支 -> 合并点
  segments.forEach(seg => {
    if (seg.type !== 'parallel') return
    seg.children.forEach(child => {
      if (seg.prev) {
        links.push({ source: String(seg.prev.id), target: String(child.id), lineStyle: { ...branchLinkStyle } })
      }
      if (seg.merge) {
        links.push({ source: String(child.id), target: String(seg.merge.id), lineStyle: { ...branchLinkStyle } })
      }
    })
  })

  function getSegmentHeadId(seg: typeof segments[0]): number | undefined {
    if (seg.type === 'node') return seg.node.id
    if (seg.children.length) return seg.children[0].id
    return undefined
  }

  function getSegmentTailId(seg: typeof segments[0]): number | undefined {
    if (seg.type === 'node') return seg.node.id
    if (seg.merge) return seg.merge.id
    if (seg.children.length) return seg.children[seg.children.length - 1].id
    return undefined
  }

  const nodeMeta: Record<string, { label: string; color: string; symbol: string; size: number | number[] }> = {
    START: { label: '开始', color: '#67c23a', symbol: 'circle', size: 70 },
    APPROVAL: { label: '审批', color: '#409eff', symbol: 'roundRect', size: [110, 60] },
    CONDITION: { label: '条件', color: '#e6a23c', symbol: 'diamond', size: 80 },
    PARALLEL_START: { label: '并行开始', color: '#9254de', symbol: 'roundRect', size: [110, 50] },
    PARALLEL_END: { label: '并行结束', color: '#9254de', symbol: 'roundRect', size: [110, 50] },
    CALLBACK: { label: '回调', color: '#13c2c2', symbol: 'roundRect', size: [100, 50] },
    END: { label: '结束', color: '#f56c6c', symbol: 'circle', size: 70 }
  }

  const displayNodes = sorted.filter(n => !hiddenNodeIds.has(n.id))

  const data = displayNodes.map(node => {
    const meta = nodeMeta[node.nodeType] || nodeMeta.APPROVAL
    const pos = positions.get(node.id) || { x: 0, y: 0 }
    const approverText = node.approvers ? `\n${node.approvers}` : ''
    return {
      id: String(node.id),
      name: (node.nodeName || meta.label) + approverText,
      x: pos.x,
      y: pos.y,
      symbol: meta.symbol,
      symbolSize: meta.size,
      itemStyle: { color: meta.color },
      label: {
        show: true,
        formatter: '{b}',
        fontSize: 12,
        color: '#fff'
      }
    }
  })

  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const n = sorted.find(x => String(x.id) === p.data.id)
        if (!n) return ''
        return `<div style="text-align:left">
          <b>${n.nodeName || n.nodeType}</b><br/>
          类型: ${n.nodeType}<br/>
          ${n.approvers ? '审批人: ' + n.approvers + '<br/>' : ''}
          ${n.conditionExpression ? '条件: ' + n.conditionExpression + '<br/>' : ''}
          ${n.parallelGroup ? '并行组: ' + n.parallelGroup + '<br/>' : ''}
        </div>`
      }
    },
    animationDurationUpdate: 500,
    animationEasingUpdate: 'quinticInOut',
    series: [
      {
        type: 'graph',
        layout: 'none',
        symbolSize: 60,
        roam: true,
        label: { show: true },
        edgeSymbol: ['circle', 'arrow'],
        edgeSymbolSize: [4, 10],
        edgeLabel: { fontSize: 12 },
        data,
        links,
        lineStyle: { opacity: 0.9, width: 2, curveness: 0, color: '#909399' },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4 }
        }
      }
    ]
  }
}

async function handleSaveNodes() {
  if (!selectedDef.value) return
  nodeSaving.value = true
  try {
    const payload = {
      definitionKey: selectedDef.value.definitionKey,
      definitionName: selectedDef.value.definitionName,
      description: selectedDef.value.description,
      category: selectedDef.value.category,
      nodes: nodes.value.map(n => ({
        ...n,
        id: (n as any).id || undefined
      }))
    }
    await updateWorkflowDefinition(selectedDef.value.id!, payload)
    ElMessage.success('节点配置保存成功')
    loadNodes(selectedDef.value.id!)
  } catch {
    /* error handled by interceptor */
  } finally {
    nodeSaving.value = false
  }
}

// ==================== 定义弹窗 ====================
const defDialogVisible = ref(false)
const defSaving = ref(false)
const isEditDef = ref(false)
const editDefId = ref<number | null>(null)
const defFormRef = ref<FormInstance>()
const defForm = reactive({
  definitionKey: '',
  definitionName: '',
  description: '',
  category: ''
})

const defFormRules: FormRules = {
  definitionKey: [{ required: true, message: '请输入定义标识', trigger: 'blur' }],
  definitionName: [{ required: true, message: '请输入定义名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

function handleAdd() {
  isEditDef.value = false
  editDefId.value = null
  defForm.definitionKey = ''
  defForm.definitionName = ''
  defForm.description = ''
  defForm.category = ''
  defDialogVisible.value = true
}

function handleEdit(row: WorkflowDefinition) {
  isEditDef.value = true
  editDefId.value = row.id!
  defForm.definitionKey = row.definitionKey
  defForm.definitionName = row.definitionName
  defForm.description = row.description || ''
  defForm.category = row.category || ''
  defDialogVisible.value = true
}

async function handleSaveDef() {
  const valid = await defFormRef.value?.validate().catch(() => false)
  if (!valid) return
  defSaving.value = true
  try {
    if (isEditDef.value && editDefId.value) {
      // 编辑时保留已有节点（节点通过右侧面板单独管理）
      let existingNodes: any[] = []
      if (selectedDef.value?.id === editDefId.value) {
        existingNodes = nodes.value.map(n => ({ ...n }))
      } else {
        try {
          const detail = await getWorkflowDefinitionDetail(editDefId.value)
          existingNodes = (detail?.data?.nodes || []).map((n: any) => ({ ...n }))
        } catch { existingNodes = [{ nodeName: '审批节点', nodeType: 'APPROVAL', approverStrategy: 'SPECIFIC', approvers: '', sortOrder: 0 }] }
      }
      await updateWorkflowDefinition(editDefId.value, {
        definitionKey: defForm.definitionKey,
        definitionName: defForm.definitionName,
        description: defForm.description,
        category: defForm.category,
        nodes: existingNodes
      })
      ElMessage.success('流程定义更新成功')
    } else {
      await createWorkflowDefinition({
        definitionKey: defForm.definitionKey,
        definitionName: defForm.definitionName,
        description: defForm.description,
        category: defForm.category,
        nodes: [{ nodeName: '审批节点', nodeType: 'APPROVAL', approverStrategy: 'SPECIFIC', approvers: '', sortOrder: 0 }]
      })
      ElMessage.success('流程定义创建成功')
    }
    defDialogVisible.value = false
    loadDefinitions()
  } catch {
    /* error handled by interceptor */
  } finally {
    defSaving.value = false
  }
}

async function handleDelete(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(
      `确定删除流程定义 "${row.definitionName}" 吗？删除后将无法恢复。`,
      '提示',
      { type: 'warning' }
    )
    await deleteWorkflowDefinition(row.id!)
    ElMessage.success('删除成功')
    if (selectedDef.value?.id === row.id) {
      selectedDef.value = null
      nodes.value = []
    }
    loadDefinitions()
  } catch {
    /* cancelled */
  }
}

async function handleToggleStatus(row: WorkflowDefinition) {
  const newStatus = row.status === 1 ? false : true
  const actionText = newStatus ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}流程定义 "${row.definitionName}" 吗？`, '提示', {
      type: 'warning'
    })
    await toggleDefinitionStatus(row.id!, newStatus)
    ElMessage.success(`${actionText}成功`)
    loadDefinitions()
  } catch {
    /* cancelled */
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadDefinitions()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
.filter-container {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
}
.def-card,
.node-card {
  margin-bottom: 16px;
}

.empty-hint {
  text-align: center;
  color: #909399;
  padding: 60px 0;
  font-size: 14px;
}

.node-list {
  min-height: 200px;
}

.node-card-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fafafa;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #dcdfe6;
}

.drag-handle {
  cursor: grab;
  color: #909399;
  font-size: 18px;
}

.node-index {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.node-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.save-nodes-bar {
  text-align: center;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.preview-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.flow-chart {
  width: 100%;
  height: 500px;
  background: #f5f7fa;
  border-radius: 6px;
}
</style>
