<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="searchForm.keyword"
          placeholder="角色编码 / 角色名称"
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
        <el-button v-permission="'system:role:edit'" type="warning" @click="handleBatchAssign">批量分配</el-button>
        <el-button v-permission="'system:role:add'" type="success" @click="handleAdd">新增角色</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="roleList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column prop="roleName" label="角色名称" width="160" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:edit'" size="small" @click="handleMenu(row)">分配菜单</el-button>
            <el-button v-permission="'system:role:edit'" size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:role:edit'"
              size="small"
              :type="row.enabled ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button v-permission="'system:role:delete'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingRole.id ? '编辑角色' : '新增角色'" width="500px">
      <el-form :model="editingRole" label-width="80px">
        <el-form-item label="角色编码"><el-input v-model="editingRole.roleCode" :disabled="!!editingRole.id" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="editingRole.roleName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editingRole.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单弹窗 -->
    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="500px">
      <el-alert type="info" :closable="false" class="menu-hint">
        勾选父级将自动勾选所有子级；勾选子级也会自动勾选所有父级
      </el-alert>
      <el-tree
        ref="menuTreeRef"
        :data="menuTreeData"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :default-checked-keys="checkedMenuIds"
        @check="onMenuTreeCheck"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveMenus">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量分配角色弹窗 -->
    <el-dialog v-model="batchDialogVisible" title="批量分配角色" width="800px" @opened="onBatchDialogOpened">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        选择一个角色，再勾选多个用户，将该角色增量分配给所选用户（不清除已有角色）
      </el-alert>

      <!-- 角色选择 -->
      <el-form label-width="80px" style="margin-bottom: 12px">
        <el-form-item label="分配角色">
          <el-select
            v-model="batchForm.roleId"
            placeholder="请选择要分配的角色"
            filterable
            style="width: 320px"
          >
            <el-option
              v-for="r in allRoles"
              :key="r.id"
              :label="`${r.roleName} (${r.roleCode})`"
              :value="r.id"
            />
          </el-select>
          <span style="margin-left: 12px; color: #909399; font-size: 13px">
            已选 <b style="color: #409eff">{{ selectedUserMap.size }}</b> 个用户
          </span>
        </el-form-item>
      </el-form>

      <!-- 用户筛选 -->
      <div class="user-filter-bar">
        <el-input
          v-model="batchUserKeyword"
          placeholder="用户名 / 昵称"
          clearable
          style="width: 200px"
          @keyup.enter="loadBatchUsers(1)"
        />
        <el-select v-model="batchUserEnabled" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-button type="primary" @click="loadBatchUsers(1)">查询</el-button>
      </div>

      <!-- 用户表格（多选） -->
      <el-table
        ref="batchUserTableRef"
        :data="batchUserList"
        v-loading="batchUserLoading"
        border
        stripe
        max-height="380"
        style="width: 100%"
        @selection-change="onBatchSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column type="index" label="#" width="45" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="userType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.userType === 'ADMIN' ? 'danger' : 'info'">
              {{ row.userType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleNames" label="已有角色" min-width="180">
          <template #default="{ row }">
            <template v-if="row.roleNames && row.roleNames.length">
              <el-tag v-for="r in row.roleNames" :key="r" size="small" style="margin: 2px">{{ r }}</el-tag>
            </template>
            <span v-else style="color: #c0c4cc">无</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 用户分页 -->
      <el-pagination
        v-model:current-page="batchUserPage.page"
        v-model:page-size="batchUserPage.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="batchUserPage.total"
        layout="total, sizes, prev, pager, next"
        background
        small
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="loadBatchUsers()"
        @current-change="loadBatchUsers()"
      />

      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!batchForm.roleId || selectedUserMap.size === 0"
          @click="handleSaveBatchAssign"
        >
          确认分配（{{ selectedUserMap.size }} 个用户）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import { getRolesPage, createRole, updateRole, toggleRoleStatus, deleteRole, assignRoleMenus, getMenuTree, getRoles, getUsersPage, batchAssignRoles } from '@/api/system'
import type { RoleInfo, MenuInfo, RoleRequest, UserInfo } from '@/types'

const loading = ref(false)
const roleList = ref<RoleInfo[]>([])
const searchForm = reactive({ keyword: '', enabled: undefined as boolean | undefined })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const editingRole = reactive<Partial<RoleInfo>>({})
const menuTreeData = ref<MenuInfo[]>([])
const checkedMenuIds = ref<number[]>([])
const currentRoleId = ref<number>(0)
const menuTreeRef = ref<InstanceType<typeof ElTree>>()

async function loadData() {
  loading.value = true
  try {
    const res = await getRolesPage({
      keyword: searchForm.keyword || undefined,
      enabled: searchForm.enabled,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    const data = res?.data
    roleList.value = data?.records || []
    pagination.total = data?.total || 0
  } catch { roleList.value = [] }
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
  Object.assign(editingRole, { id: undefined, roleCode: '', roleName: '', sortOrder: 0 })
  dialogVisible.value = true
}

function handleEdit(row: RoleInfo) {
  Object.assign(editingRole, row)
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (editingRole.id) {
      await updateRole(editingRole.id, editingRole as RoleRequest)
    } else {
      await createRole(editingRole as RoleRequest)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { /* */ }
}

async function handleToggle(row: RoleInfo) {
  try {
    await toggleRoleStatus(row.id, !row.enabled)
    ElMessage.success('操作成功')
    loadData()
  } catch { /* */ }
}

async function handleDelete(row: RoleInfo) {
  try {
    await ElMessageBox.confirm(`确定删除角色 "${row.roleName}" 吗？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* */ }
}

/** 将树形菜单展开为 id→parentId 的扁平映射 */
function flattenMenuTree(nodes: MenuInfo[]): Record<number, number> {
  const map: Record<number, number> = {}
  function walk(items: MenuInfo[]) {
    for (const item of items) {
      map[item.id] = item.parentId
      if (item.children) walk(item.children)
    }
  }
  walk(nodes)
  return map
}

/** 收集某个节点下所有子节点的 ID（递归） */
function collectChildIds(node: MenuInfo): number[] {
  const ids: number[] = []
  if (node.children && node.children.length > 0) {
    for (const child of node.children) {
      ids.push(child.id)
      ids.push(...collectChildIds(child))
    }
  }
  return ids
}

/** check-strictly 模式下的父子联动：勾选父级自动勾选全部子级；勾选子级自动勾选全部父级 */
function onMenuTreeCheck(node: MenuInfo, info: { checkedKeys: number[] }) {
  if (!menuTreeRef.value) return

  const isChecked = info.checkedKeys.includes(node.id)

  if (isChecked) {
    // 勾选 → 向上递归勾选所有父级
    const parentMap = flattenMenuTree(menuTreeData.value)
    let parentId = node.parentId
    while (parentId && parentId !== 0) {
      const parentNode = menuTreeRef.value.getNode(parentId)
      if (parentNode && !parentNode.checked) {
        menuTreeRef.value.setChecked(parentId, true, false)
      }
      parentId = parentMap[parentId] || 0
    }

    // 勾选 → 向下递归勾选所有子级
    const childIds = collectChildIds(node)
    for (const childId of childIds) {
      const childNode = menuTreeRef.value.getNode(childId)
      if (childNode && !childNode.checked) {
        menuTreeRef.value.setChecked(childId, true, false)
      }
    }
  } else {
    // 取消勾选 → 向下递归取消所有子级
    const childIds = collectChildIds(node)
    for (const childId of childIds) {
      const childNode = menuTreeRef.value.getNode(childId)
      if (childNode && childNode.checked) {
        menuTreeRef.value.setChecked(childId, false, false)
      }
    }
  }
}

async function handleMenu(row: RoleInfo) {
  currentRoleId.value = row.id
  try {
    const res = await getMenuTree()
    menuTreeData.value = res?.data || []
    checkedMenuIds.value = row.menuIds || []
  } catch { /* */ }
  menuDialogVisible.value = true
}

async function handleSaveMenus() {
  // check-strictly 模式下只保存明确勾选的节点（父目录可选，子按钮可选）
  const checkedKeys = menuTreeRef.value?.getCheckedKeys(false) as number[] || []
  try {
    await assignRoleMenus(currentRoleId.value, checkedKeys)
    ElMessage.success('菜单分配成功')
    menuDialogVisible.value = false
  } catch { /* */ }
}

// ==================== 批量分配角色 ====================

const batchDialogVisible = ref(false)
const allRoles = ref<RoleInfo[]>([])
const batchUserTableRef = ref()

// 用户表格数据
const batchUserLoading = ref(false)
const batchUserList = ref<UserInfo[]>([])
const batchUserKeyword = ref('')
const batchUserEnabled = ref<boolean | undefined>(undefined)
const batchUserPage = reactive({ page: 1, pageSize: 10, total: 0 })

// 跨页选择：用 Map 存储所有被选中的用户，key=userId
const selectedUserMap = ref(new Map<number, UserInfo>())

const batchForm = reactive({ roleId: null as number | null })

async function loadAllRoles() {
  try {
    const res = await getRoles()
    allRoles.value = res?.data || []
  } catch { allRoles.value = [] }
}

async function loadBatchUsers(page?: number) {
  if (page) batchUserPage.page = page
  batchUserLoading.value = true
  try {
    const res = await getUsersPage({
      keyword: batchUserKeyword.value || undefined,
      enabled: batchUserEnabled.value,
      page: batchUserPage.page,
      pageSize: batchUserPage.pageSize,
    })
    batchUserList.value = res?.data?.records || []
    batchUserPage.total = res?.data?.total || 0

    // 恢复当前页中被选中的行（回显勾选状态）
    await nextTick()
    const selectedIds = new Set(selectedUserMap.value.keys())
    batchUserList.value.forEach(row => {
      if (selectedIds.has(row.id)) {
        batchUserTableRef.value?.toggleRowSelection(row, true)
      }
    })
  } catch { batchUserList.value = [] }
  finally { batchUserLoading.value = false }
}

function onBatchSelectionChange(rows: UserInfo[]) {
  // 先清除当前页在 map 中的记录
  batchUserList.value.forEach(row => selectedUserMap.value.delete(row.id))
  // 重新写入当前页被勾选的行
  rows.forEach(row => selectedUserMap.value.set(row.id, row))
}

function handleBatchAssign() {
  batchForm.roleId = null
  selectedUserMap.value.clear()
  batchUserKeyword.value = ''
  batchUserEnabled.value = undefined
  batchUserPage.page = 1
  batchUserList.value = []
  if (allRoles.value.length === 0) loadAllRoles()
  batchDialogVisible.value = true
}

function onBatchDialogOpened() {
  if (batchUserList.value.length === 0) loadBatchUsers(1)
}

async function handleSaveBatchAssign() {
  if (!batchForm.roleId || selectedUserMap.value.size === 0) {
    ElMessage.warning('请选择角色和用户')
    return
  }
  try {
    const userIds = Array.from(selectedUserMap.value.keys())
    const count = await batchAssignRoles({ userIds, roleIds: [batchForm.roleId] })
    ElMessage.success(`批量角色分配成功，共新增 ${count?.data ?? userIds.length} 条关联`)
    batchDialogVisible.value = false
  } catch { /* */ }
}

onMounted(() => {
  loadData()
  loadAllRoles()
})
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.menu-hint { margin-bottom: 12px; }
.user-filter-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
</style>
