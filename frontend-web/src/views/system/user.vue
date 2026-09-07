<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="searchForm.keyword"
          placeholder="用户名 / 昵称 / 邮箱"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.enabled" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-select v-model="searchForm.userType" placeholder="类型" clearable style="width: 120px">
          <el-option label="管理员" value="admin" />
          <el-option label="普通用户" value="user" />
          <el-option label="服务账号" value="service" />
        </el-select>
        <el-tree-select
          v-model="searchForm.deptId"
          :data="deptTree"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          placeholder="部门"
          clearable
          check-strictly
          style="width: 180px"
        />
        <el-select v-model="searchForm.roleId" placeholder="角色" clearable style="width: 150px">
          <el-option v-for="r in allRoles" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 用户列表 -->
    <el-card shadow="never">
      <div class="card-header">
        <div class="batch-actions" v-if="selectedUserIds.length > 0">
          <span>已选 <b>{{ selectedUserIds.length }}</b> 个用户</span>
          <el-button v-permission="'system:user:edit'" type="warning" size="small" @click="handleBatchRoles">批量分配角色</el-button>
          <el-button size="small" @click="selectedUserIds = []">取消选择</el-button>
        </div>
        <span v-else></span>
        <el-button v-permission="'system:user:edit'" type="primary" @click="handleAdd">新增用户</el-button>
      </div>
      <el-table :data="userList" v-loading="loading" border stripe style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="username" label="用户名" width="110" />
        <el-table-column prop="nickname" label="昵称" width="110" />
        <el-table-column prop="email" label="邮箱" min-width="150" />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column prop="deptName" label="部门" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.deptName" size="small">{{ row.deptName }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="130">
          <template #default="{ row }">
            <template v-if="row.roleCodes && row.roleCodes.length">
              <el-tag v-for="(code, i) in row.roleCodes" :key="i" size="small" type="warning" style="margin: 1px">{{ code }}</el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="已分配应用" min-width="160">
          <template #default="{ row }">
            <template v-if="userAppsMap[row.id]?.length">
              <el-popover
                placement="bottom"
                :width="280"
                trigger="hover"
                :hide-after="0"
              >
                <template #reference>
                  <span class="app-count-link">
                    {{ userAppsMap[row.id].length }} 个应用
                  </span>
                </template>
                <div class="app-popover-list">
                  <el-tag
                    v-for="app in userAppsMap[row.id]"
                    :key="app.clientId"
                    size="small"
                    style="margin: 2px"
                  >{{ app.clientName }}</el-tag>
                </div>
              </el-popover>
            </template>
            <span v-else class="text-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="userType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ userTypeLabel(row.userType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:user:edit'" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:user:edit'" size="small" @click="handleRoles(row)">分配角色</el-button>
            <el-button v-permission="'system:user:edit'" size="small" type="primary" @click="handleApps(row)">应用配置</el-button>
            <el-button
              v-permission="'system:user:edit'"
              size="small"
              :type="row.enabled ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button v-permission="'system:user:delete'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑用户弹窗 -->
    <el-dialog v-model="userDialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form ref="userFormRef" :model="userForm" :rules="userFormRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" placeholder="请输入用户名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" :prop="isEdit ? '' : 'password'">
          <el-input
            v-model="userForm.password"
            type="password"
            :placeholder="isEdit ? '留空则不修改密码' : '请输入密码'"
            show-password
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="用户类型" prop="userType">
          <el-select v-model="userForm.userType" placeholder="请选择用户类型" style="width: 100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
            <el-option label="服务账号" value="service" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-tree-select
            v-model="userForm.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="角色" prop="roleIds">
          <el-select v-model="userForm.roleIds" multiple placeholder="不选则自动分配默认角色" style="width: 100%">
            <el-option v-for="r in allRoles" :key="r.id" :label="r.roleName + ' (' + r.roleCode + ')'" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="handleSaveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px">
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">
          {{ role.roleName }} ({{ role.roleCode }})
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量分配角色弹窗 -->
    <el-dialog v-model="batchRoleDialogVisible" title="批量分配角色" width="500px">
      <el-alert type="info" :closable="false" class="batch-hint">
        将选中的 <b>{{ selectedUserIds.length }}</b> 个用户批量分配到指定角色（增量添加，不清除已有角色）
      </el-alert>
      <el-checkbox-group v-model="batchSelectedRoleIds" style="margin-top: 12px">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">
          {{ role.roleName }} ({{ role.roleCode }})
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="batchRoleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveBatchRoles">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 应用配置弹窗 -->
    <el-dialog v-model="appDialogVisible" :title="'应用配置 - ' + (currentUser?.username || '')" width="640px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        为 <b>{{ currentUser?.nickname || currentUser?.username }}</b> 分配可访问的应用，用户仅能看到被分配的应用。
      </el-alert>
      <div v-loading="appLoading">
        <el-checkbox-group v-model="selectedClientIds">
          <div v-for="app in allApps" :key="app.clientId" class="app-item">
            <el-checkbox :label="app.clientId" :value="app.clientId">
              <div class="app-info">
                <span class="app-name">{{ app.clientName }}</span>
                <span class="app-id">({{ app.clientId }})</span>
                <el-tag v-if="app.scopes" size="small" type="info" class="app-scope">{{ app.scopes }}</el-tag>
              </div>
            </el-checkbox>
          </div>
        </el-checkbox-group>
        <el-empty v-if="allApps.length === 0 && !appLoading" description="暂无可用应用" />
      </div>
      <template #footer>
        <el-button @click="appDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="appSaving" @click="handleSaveApps">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUsersPage, toggleUserStatus, deleteUser, getUserRoles, assignUserRoles, getDeptTree, getRoles, createUser, updateUser, batchAssignRoles, getUserSubsystems, assignUserSubsystems, listAllSubsystems } from '@/api/system'
import type { UserSubsystemVO, SubsystemVO } from '@/api/system'
import type { UserInfo, RoleInfo, DeptInfo, UserCreateRequest, UserUpdateRequest } from '@/types'

const loading = ref(false)
const userList = ref<UserInfo[]>([])
const searchForm = reactive({
  keyword: '',
  enabled: undefined as boolean | undefined,
  userType: undefined as string | undefined,
  deptId: undefined as number | undefined,
  roleId: undefined as number | undefined,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

// 批量操作
const selectedUserIds = ref<number[]>([])
function handleSelectionChange(rows: UserInfo[]) {
  selectedUserIds.value = rows.map(r => r.id)
}

// 新增/编辑弹窗
const userDialogVisible = ref(false)
const userSaving = ref(false)
const isEdit = ref(false)
const editUserId = ref<number | null>(null)
const userFormRef = ref<FormInstance>()
const userForm = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  userType: 'user' as string,
  deptId: undefined as number | undefined,
  roleIds: [] as number[],
})

const dialogTitle = computed(() => (isEdit.value ? '编辑用户' : '新增用户'))

const userFormRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在3-50字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度需在6-100字符之间', trigger: 'blur' },
  ],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }],
}

// 分配角色
const roleDialogVisible = ref(false)
const batchRoleDialogVisible = ref(false)
const allRoles = ref<RoleInfo[]>([])
const selectedRoleIds = ref<number[]>([])
const batchSelectedRoleIds = ref<number[]>([])
const currentUser = ref<UserInfo | null>(null)
const deptTree = ref<DeptInfo[]>([])

const userTypeMap: Record<string, string> = {
  admin: '管理员',
  user: '普通用户',
  service: '服务账号',
}

function userTypeLabel(type?: string) {
  return userTypeMap[type || ''] || type || '普通'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUsersPage({
      keyword: searchForm.keyword || undefined,
      enabled: searchForm.enabled,
      userType: searchForm.userType,
      deptId: searchForm.deptId,
      roleId: searchForm.roleId,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    const data = res?.data
    userList.value = data?.records || []
    pagination.total = data?.total || 0
    // 用户列表加载后批量拉取应用分配数据
    loadUserApps(userList.value)
  } catch {
    userList.value = []
  } finally {
    loading.value = false
  }
}

// ==================== 用户应用分配映射 ====================

const userAppsMap = ref<Record<number, UserSubsystemVO[]>>({})

async function loadUserApps(users: UserInfo[]) {
  if (!users.length) return
  userAppsMap.value = {}
  const results = await Promise.allSettled(
    users.map(async u => {
      const res = await getUserSubsystems(u.id)
      return { userId: u.id, apps: res?.data || [] as UserSubsystemVO[] }
    })
  )
  const map: Record<number, UserSubsystemVO[]> = {}
  for (const r of results) {
    if (r.status === 'fulfilled') {
      map[r.value.userId] = r.value.apps
    }
  }
  userAppsMap.value = map
}

async function loadDepts() {
  try {
    const res = await getDeptTree()
    deptTree.value = res?.data || []
  } catch {
    deptTree.value = []
  }
}

async function loadAllRoles() {
  try {
    const res = await getRoles()
    allRoles.value = res?.data || []
  } catch {
    ElMessage.warning('角色列表加载失败，请检查权限或网络')
    allRoles.value = []
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.enabled = undefined
  searchForm.userType = undefined
  searchForm.deptId = undefined
  searchForm.roleId = undefined
  pagination.page = 1
  loadData()
}

// ==================== 新增/编辑 ====================

function resetUserForm() {
  userForm.username = ''
  userForm.password = ''
  userForm.nickname = ''
  userForm.email = ''
  userForm.phone = ''
  userForm.userType = 'user'
  userForm.deptId = undefined
  userForm.roleIds = []
  userFormRef.value?.resetFields()
}

function handleAdd() {
  isEdit.value = false
  editUserId.value = null
  resetUserForm()
  userDialogVisible.value = true
}

function handleEdit(row: UserInfo) {
  isEdit.value = true
  editUserId.value = row.id
  userForm.username = row.username
  userForm.password = ''
  userForm.nickname = row.nickname || ''
  userForm.email = row.email || ''
  userForm.phone = row.phone || ''
  userForm.userType = row.userType || 'user'
  userForm.deptId = row.deptId
  userDialogVisible.value = true
}

async function handleSaveUser() {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return

  userSaving.value = true
  try {
    if (isEdit.value && editUserId.value) {
      const data: UserUpdateRequest = {
        nickname: userForm.nickname || undefined,
        email: userForm.email || undefined,
        phone: userForm.phone || undefined,
        userType: userForm.userType,
        deptId: userForm.deptId,
      }
      if (userForm.password) {
        data.password = userForm.password
      }
      await updateUser(editUserId.value, data)
      ElMessage.success('用户修改成功')
    } else {
      const data: UserCreateRequest = {
        username: userForm.username,
        password: userForm.password,
        nickname: userForm.nickname || undefined,
        email: userForm.email || undefined,
        phone: userForm.phone || undefined,
        userType: userForm.userType,
        deptId: userForm.deptId,
        roleIds: userForm.roleIds.length > 0 ? userForm.roleIds : undefined,
      }
      await createUser(data)
      ElMessage.success('用户创建成功')
    }
    userDialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    userSaving.value = false
  }
}

// ==================== 禁用/启用 ====================

async function handleToggle(row: UserInfo) {
  try {
    await toggleUserStatus(row.id, !row.enabled)
    ElMessage.success(`${row.enabled ? '禁用' : '启用'}成功`)
    loadData()
  } catch { /* handled by interceptor */ }
}

async function handleDelete(row: UserInfo) {
  try {
    await ElMessageBox.confirm(`确定删除用户 "${row.username}" 吗？`, '提示', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* cancelled */ }
}

// ==================== 分配角色 ====================

async function handleRoles(row: UserInfo) {
  currentUser.value = row
  selectedRoleIds.value = []
  // 确保角色列表已加载
  if (allRoles.value.length === 0) await loadAllRoles()
  try {
    const userRolesRes = await getUserRoles(row.id)
    const userRoleCodes: string[] = userRolesRes?.data || []
    selectedRoleIds.value = allRoles.value
      .filter(role => userRoleCodes.includes(role.roleCode))
      .map(role => role.id)
  } catch {
    ElMessage.error('获取用户角色失败')
    selectedRoleIds.value = []
  }
  roleDialogVisible.value = true
}

async function handleSaveRoles() {
  if (!currentUser.value) return
  try {
    await assignUserRoles(currentUser.value.id, selectedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadData()
  } catch { /* */ }
}

// ==================== 批量分配角色 ====================

function handleBatchRoles() {
  if (selectedUserIds.value.length === 0) {
    ElMessage.warning('请先选择用户')
    return
  }
  batchSelectedRoleIds.value = []
  if (allRoles.value.length === 0) loadAllRoles()
  batchRoleDialogVisible.value = true
}

async function handleSaveBatchRoles() {
  if (batchSelectedRoleIds.value.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  try {
    await batchAssignRoles({
      userIds: selectedUserIds.value,
      roleIds: batchSelectedRoleIds.value,
    })
    ElMessage.success('批量角色分配成功')
    batchRoleDialogVisible.value = false
    selectedUserIds.value = []
    loadData()
  } catch { /* */ }
}

// ==================== 应用配置 ====================

const appDialogVisible = ref(false)
const appLoading = ref(false)
const appSaving = ref(false)
const allApps = ref<SubsystemVO[]>([])
const selectedClientIds = ref<string[]>([])

async function handleApps(row: UserInfo) {
  currentUser.value = row
  selectedClientIds.value = []
  appDialogVisible.value = true
  appLoading.value = true
  try {
    const [appsRes, userAppsRes] = await Promise.all([
      listAllSubsystems(),
      getUserSubsystems(row.id),
    ])
    allApps.value = appsRes?.data || []
    const userApps = userAppsRes?.data || []
    selectedClientIds.value = userApps.map((a: UserSubsystemVO) => a.clientId)
  } catch {
    allApps.value = []
    selectedClientIds.value = []
  } finally {
    appLoading.value = false
  }
}

async function handleSaveApps() {
  if (!currentUser.value) return
  appSaving.value = true
  try {
    await assignUserSubsystems(currentUser.value.id, selectedClientIds.value)
    ElMessage.success('应用配置保存成功')
    appDialogVisible.value = false
  } catch { /* handled by interceptor */ } finally {
    appSaving.value = false
  }
}

onMounted(() => {
  loadDepts()
  loadAllRoles()
  loadData()
})
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.text-muted { color: #999; }
.batch-actions { display: flex; align-items: center; gap: 10px; }
.batch-hint { margin-bottom: 0; }

/* 应用配置 */
.app-item {
  padding: 8px 12px;
  margin-bottom: 4px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  transition: border-color 0.2s;
}
.app-item:hover { border-color: #409eff; }
.app-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.app-name { font-weight: 500; color: #303133; }
.app-id { font-size: 12px; color: #909399; font-family: monospace; }
.app-scope { margin-left: auto; }

/* 用户列表应用列 */
.app-count-link {
  color: #409eff;
  cursor: pointer;
  font-size: 13px;
}
.app-count-link:hover { text-decoration: underline; }
.app-popover-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
