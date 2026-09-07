<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="searchForm.keyword"
          placeholder="客户端ID / 名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.enabled" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="success" @click="handleAdd" v-permission="'auth:client:add'">注册客户端</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="clientList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="clientId" label="客户端ID" min-width="140" />
        <el-table-column prop="clientName" label="名称" width="140" />
        <el-table-column label="子系统" min-width="260">
          <template #default="{ row }">
            <el-tag
              v-for="s in (row.subsystems || [])"
              :key="s.id"
              size="small"
              :type="s.visiblePortal ? 'success' : 'info'"
              effect="plain"
              class="subsystem-tag"
              style="margin: 2px; cursor: pointer"
              @click.stop="openSubsystemDetail(s)"
            >
              <el-image
                v-if="s.iconUrl"
                :src="s.iconUrl"
                style="width: 14px; height: 14px; vertical-align: middle; margin-right: 4px"
                fit="cover"
                :preview-src-list="[]"
              />
              <el-icon v-else style="vertical-align: middle; margin-right: 2px"><Grid /></el-icon>
              <el-tag size="small" :type="codeTagType(s.code)" effect="dark" style="margin-right: 4px; height: 16px; line-height: 16px; padding: 0 4px">
                {{ codeLabel(s.code) }}
              </el-tag>
              {{ s.name }}
            </el-tag>
            <span v-if="!row.subsystems || row.subsystems.length === 0" class="text-placeholder">
              尚未配置
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="scopes" label="授权范围" min-width="120">
          <template #default="{ row }">
            <el-tag v-for="s in (row.scopes || [])" :key="s" size="small" style="margin: 2px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="grantTypes" label="授权类型" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="g in (row.grantTypes || [])" :key="g" type="success" size="small" style="margin: 2px">{{ g }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tokenTtl" label="Token有效期" width="100" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" v-permission="'auth:client:edit'" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" v-permission="'auth:client:edit'" @click="handleResetSecret(row)">重置密钥</el-button>
            <el-button size="small" v-permission="'auth:client:edit'" :type="row.enabled ? 'danger' : 'success'" @click="handleToggle(row)">
              {{ row.enabled ? '下线' : '上线' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        class="pagination-container"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </el-card>

    <!-- 注册/编辑客户端弹窗（子系统内联在底部） -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingClient.id ? '编辑客户端' : '注册客户端'"
      width="840px"
      :close-on-click-modal="false"
      top="5vh"
    >
      <el-form :model="editingClient" label-width="100px">
        <!-- ============ 1. 客户端基本信息 ============ -->
        <div class="form-section-title">基本信息</div>
        <el-form-item label="客户端ID">
          <el-input v-model="editingClient.clientId" :disabled="!!editingClient.id" />
        </el-form-item>
        <el-form-item label="客户端名称">
          <el-input v-model="editingClient.clientName" placeholder="如：个人博客平台" />
        </el-form-item>
        <el-form-item label="客户端密钥" v-if="!editingClient.id">
          <el-input v-model="editingClient.clientSecret" placeholder="留空自动生成" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="授权范围">
              <el-select v-model="scopeArray" multiple filterable allow-create style="width: 100%" placeholder="输入后回车" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权类型">
              <el-select v-model="grantTypeArray" multiple style="width: 100%">
                <el-option label="authorization_code" value="authorization_code" />
                <el-option label="refresh_token" value="refresh_token" />
                <el-option label="client_credentials" value="client_credentials" />
                <el-option label="password" value="password" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Token有效期(秒)">
              <el-input-number v-model="editingClient.tokenTtl" :min="60" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="刷新有效期(秒)">
              <el-input-number v-model="editingClient.refreshTtl" :min="120" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ============ 2. 子系统列表（内联维护） ============ -->
        <el-divider content-position="left">
          <span class="form-section-title">子系统（维护回调地址）</span>
          <span class="form-section-hint">· 客户端不再单独维护回调地址，OAuth2 回调地址统一从子系统聚合</span>
        </el-divider>

        <div class="subsystem-list">
          <div
            v-for="(item, idx) in subsystemItems"
            :key="idx"
            class="subsystem-row"
          >
            <div class="subsystem-row-header">
              <span class="subsystem-index">#{{ idx + 1 }}</span>
              <el-button
                type="danger"
                :icon="Delete"
                circle
                size="small"
                @click="removeSubsystem(idx)"
                :disabled="subsystemItems.length <= 1"
              />
            </div>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="子系统名称" :label-width="'90px'" required>
                  <el-input v-model="item.name" placeholder="如：博客 Web、博客 小程序" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="回调URL" :label-width="'90px'" required>
                  <el-input v-model="item.redirectUri" placeholder="http://127.0.0.1:9005/oauth2/callback" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="平台标识" :label-width="'90px'" required>
                  <el-select v-model="item.code" style="width: 100%">
                    <el-option
                      v-for="opt in PLATFORM_OPTIONS"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    >
                      <span style="float: left">{{ opt.label }}</span>
                      <span style="float: right; color: #999; font-size: 12px">{{ opt.value }}</span>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="图标URL" :label-width="'90px'">
                  <el-input v-model="item.iconUrl" placeholder="https://cdn.example.com/icon.png" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="14">
                <el-form-item label="排序" :label-width="'90px'">
                  <el-input-number v-model="item.sortOrder" :min="0" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="10">
                <el-form-item label="门户展示" :label-width="'90px'">
                  <el-switch v-model="item.visiblePortal" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="子系统描述" :label-width="'90px'">
              <el-input v-model="item.description" type="textarea" :rows="2" placeholder="子系统功能描述" />
            </el-form-item>
          </div>
          <el-button
            type="primary"
            plain
            :icon="Plus"
            @click="addSubsystem"
            style="width: 100%; margin-top: 4px"
          >
            + 添加子系统
          </el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 子系统详情弹窗（点击子系统标签时触发） -->
    <el-dialog
      v-model="detailVisible"
      title="子系统详情"
      width="520px"
      :close-on-click-modal="true"
      top="15vh"
    >
      <div v-if="detailSubsystem" class="subsystem-detail">
        <div class="detail-banner">
          <el-image
            v-if="detailSubsystem.iconUrl"
            :src="detailSubsystem.iconUrl"
            class="detail-icon"
            fit="cover"
          />
          <div v-else class="detail-icon-fallback" :style="{ background: iconBg(detailSubsystem.id?.toString() || detailSubsystem.name) }">
            <span>{{ (detailSubsystem.name || '?').charAt(0) }}</span>
          </div>
          <div class="detail-title">
            <h3>{{ detailSubsystem.name }}</h3>
            <el-tag size="small" :type="codeTagType(detailSubsystem.code)" effect="dark">
              {{ codeLabel(detailSubsystem.code) }} · {{ detailSubsystem.code }}
            </el-tag>
          </div>
        </div>

        <el-descriptions :column="1" border size="default" class="detail-desc">
          <el-descriptions-item label="子系统ID">
            {{ detailSubsystem.id }}
          </el-descriptions-item>
          <el-descriptions-item label="所属客户端">
            <span>{{ detailSubsystem.clientName || detailSubsystem.clientId }}</span>
            <span class="text-muted"> ({{ detailSubsystem.clientId }})</span>
          </el-descriptions-item>
          <el-descriptions-item label="回调URL">
            <a v-if="detailSubsystem.redirectUri" :href="detailSubsystem.redirectUri" target="_blank" rel="noopener">
              {{ detailSubsystem.redirectUri }}
            </a>
            <span v-else class="text-placeholder">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="图标URL">
            <a v-if="detailSubsystem.iconUrl" :href="detailSubsystem.iconUrl" target="_blank" rel="noopener" class="icon-url">
              {{ detailSubsystem.iconUrl }}
            </a>
            <span v-else class="text-placeholder">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="排序号">
            {{ detailSubsystem.sortOrder ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="门户展示">
            <el-tag :type="detailSubsystem.visiblePortal ? 'success' : 'info'" size="small">
              {{ detailSubsystem.visiblePortal ? '已展示' : '已隐藏' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="描述">
            <span v-if="detailSubsystem.description">{{ detailSubsystem.description }}</span>
            <span v-else class="text-placeholder">-</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!detailSubsystem?.redirectUri" @click="goToSubsystem">
          打开回调地址
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Grid } from '@element-plus/icons-vue'
import { getClientsPage, createClient, updateClient, resetClientSecret, toggleClientStatus } from '@/api/auth'
import type { ClientInfo, ClientRequest, ClientSubsystemItem, SubsystemInfo } from '@/types'

const loading = ref(false)
const clientList = ref<ClientInfo[]>([])
const searchForm = reactive({ keyword: '', enabled: undefined as boolean | undefined })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

// 编辑弹窗状态
const dialogVisible = ref(false)
const saving = ref(false)
const editingClient = reactive<Partial<ClientInfo> & { clientSecret?: string }>({})
const scopeArray = ref<string[]>([])
const grantTypeArray = ref<string[]>(['authorization_code', 'refresh_token'])

// 子系统列表（内联编辑）
const subsystemItems = ref<ClientSubsystemItem[]>([])

// 平台标识选项（与后端 ClientSubsystemItem.code 约束保持一致）
const PLATFORM_OPTIONS = [
  { value: 'web', label: 'Web 端' },
  { value: 'miniapp', label: '小程序端' },
  { value: 'app', label: '移动 App' },
  { value: 'desktop', label: '桌面客户端' },
  { value: 'admin', label: '管理后台' },
] as const

function codeLabel(code?: string): string {
  if (!code) return 'Web 端'
  return PLATFORM_OPTIONS.find((o) => o.value === code)?.label || code
}

function codeTagType(code?: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  switch (code) {
    case 'miniapp': return 'success'
    case 'app': return 'warning'
    case 'desktop': return 'info'
    case 'admin': return 'danger'
    case 'web':
    default: return 'primary'
  }
}

// 子系统详情弹窗状态
const detailVisible = ref(false)
const detailSubsystem = ref<SubsystemInfo | null>(null)

function openSubsystemDetail(sub: SubsystemInfo) {
  detailSubsystem.value = sub
  detailVisible.value = true
}

function goToSubsystem() {
  const url = detailSubsystem.value?.redirectUri
  if (url) {
    window.open(url, '_blank')
  }
}

// 详情弹窗 fallback 图标调色板
const DETAIL_COLOR_PALETTE = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #a18cd1, #fbc2eb)',
  'linear-gradient(135deg, #fccb90, #d57eeb)',
  'linear-gradient(135deg, #ff9a9e, #fad0c4)',
]
function iconBg(seed: string) {
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  }
  return DETAIL_COLOR_PALETTE[Math.abs(hash) % DETAIL_COLOR_PALETTE.length]
}

async function loadData() {
  loading.value = true
  try {
    const res = await getClientsPage({
      keyword: searchForm.keyword || undefined,
      enabled: searchForm.enabled,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    const data = res?.data
    clientList.value = data?.records || []
    pagination.total = data?.total || 0
  } catch { clientList.value = [] }
  finally { loading.value = false }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.enabled = undefined
  pagination.page = 1
  loadData()
}

function handleAdd() {
  Object.assign(editingClient, {
    id: undefined,
    clientId: '',
    clientName: '',
    clientSecret: '',
    scopes: [],
    grantTypes: [],
    tokenTtl: 3600,
    refreshTtl: 43200,
    enabled: true,
  })
  scopeArray.value = ['openid', 'profile']
  grantTypeArray.value = ['authorization_code', 'refresh_token']
  subsystemItems.value = [makeEmptySubsystem(0)]
  dialogVisible.value = true
}

function handleEdit(row: ClientInfo) {
  Object.assign(editingClient, row)
  scopeArray.value = [...(row.scopes || [])]
  grantTypeArray.value = [...(row.grantTypes || [])]
  // 把已有子系统映射为编辑项；保留 id 以便后端识别为更新
  subsystemItems.value = (row.subsystems && row.subsystems.length > 0)
    ? row.subsystems
        .slice()
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
        .map((s, i) => ({
          id: s.id,
          // 兼容历史数据：旧记录没有 code 时回退到 web
          code: s.code || 'web',
          name: s.name,
          iconUrl: s.iconUrl,
          redirectUri: s.redirectUri,
          description: s.description,
          sortOrder: s.sortOrder ?? i,
          visiblePortal: s.visiblePortal ?? true,
        }))
    : [makeEmptySubsystem(0)]
  dialogVisible.value = true
}

function makeEmptySubsystem(sortOrder: number): ClientSubsystemItem {
  return {
    id: undefined,
    code: 'web',
    name: '',
    iconUrl: '',
    redirectUri: '',
    description: '',
    sortOrder,
    visiblePortal: true,
  }
}

function addSubsystem() {
  subsystemItems.value.push(makeEmptySubsystem(subsystemItems.value.length))
}

function removeSubsystem(idx: number) {
  subsystemItems.value.splice(idx, 1)
  // 重排 sortOrder
  subsystemItems.value.forEach((it, i) => { it.sortOrder = i })
}

async function handleSave() {
  // 基础校验
  if (!editingClient.clientId?.trim()) {
    ElMessage.warning('请输入客户端ID')
    return
  }
  if (!editingClient.clientName?.trim()) {
    ElMessage.warning('请输入客户端名称')
    return
  }
  // 子系统校验
  const validSubs = subsystemItems.value.filter(s => s.name?.trim() && s.redirectUri?.trim())
  if (subsystemItems.value.length === 0 || validSubs.length === 0) {
    ElMessage.warning('请至少添加一个有效的子系统（名称、平台标识和回调URL必填）')
    return
  }
  // 校验每个子系统的必填项
  for (let i = 0; i < subsystemItems.value.length; i++) {
    const s = subsystemItems.value[i]
    if (!s.name?.trim()) {
      ElMessage.warning(`第 ${i + 1} 个子系统缺少名称`)
      return
    }
    if (!s.code?.trim()) {
      ElMessage.warning(`第 ${i + 1} 个子系统缺少平台标识`)
      return
    }
    if (!s.redirectUri?.trim()) {
      ElMessage.warning(`第 ${i + 1} 个子系统缺少回调URL`)
      return
    }
  }

  saving.value = true
  try {
    const payload: ClientRequest = {
      clientId: editingClient.clientId!,
      clientSecret: editingClient.clientSecret || '',
      clientName: editingClient.clientName!,
      scopes: scopeArray.value,
      grantTypes: grantTypeArray.value,
      authMethods: ['client_secret_basic', 'client_secret_post'],
      tokenTtl: editingClient.tokenTtl ?? 3600,
      refreshTtl: editingClient.refreshTtl ?? 43200,
      enabled: editingClient.enabled ?? true,
      clientSecretExpiresAt: editingClient.clientSecretExpiresAt ?? null,
      subsystems: validSubs,
    }
    if (editingClient.id) {
      await updateClient(editingClient.id, payload)
    } else {
      await createClient(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { /* */ }
  finally { saving.value = false }
}

async function handleResetSecret(row: ClientInfo) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密钥（留空自动生成）', '重置密钥', {
      inputType: 'password',
    })
    await resetClientSecret(row.id!, { secret: value || '' })
    ElMessage.success('密钥重置成功')
  } catch { /* cancelled */ }
}

async function handleToggle(row: ClientInfo) {
  try {
    await toggleClientStatus(row.id!, !row.enabled)
    ElMessage.success('操作成功')
    loadData()
  } catch { /* */ }
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.form-section-title {
  font-weight: 600;
  color: #303133;
}
.form-section-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
  font-weight: normal;
}

.text-placeholder {
  color: #c0c4cc;
  font-size: 12px;
}

.subsystem-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 480px;
  overflow-y: auto;
  padding: 4px 2px;
}

.subsystem-row {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background-color: #fafbfc;
}

.subsystem-row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.subsystem-index {
  font-weight: 600;
  color: #409eff;
  font-size: 13px;
}

::deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}

/* 列表中子系统标签（可点击进入详情） */
.subsystem-tag {
  transition: all 0.2s;
}
.subsystem-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.25);
}

/* 详情弹窗 */
.subsystem-detail {
  padding: 4px 0;
}

.detail-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #f0f6ff 0%, #e8f4ff 100%);
  border-radius: 8px;
}

.detail-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}

.detail-icon-fallback {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 26px;
  font-weight: 600;
  flex-shrink: 0;
}

.detail-title {
  flex: 1;
  min-width: 0;
}
.detail-title h3 {
  margin: 0 0 6px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.detail-desc {
  margin-top: 4px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}

.icon-url {
  word-break: break-all;
  font-size: 12px;
}
</style>
