<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="searchForm.keyword"
          placeholder="菜单名称 / 权限标识"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.menuType" placeholder="菜单类型" clearable style="width: 130px">
          <el-option label="目录" :value="0" />
          <el-option label="菜单" :value="1" />
          <el-option label="按钮" :value="2" />
        </el-select>
        <el-select v-model="searchForm.enabled" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button v-permission="'system:menu:add'" type="success" @click="handleAdd(null)">新增菜单</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table
        :data="menuTree"
        v-loading="loading"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column prop="icon" label="图标" width="80">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.menuType === 0 ? '' : row.menuType === 1 ? 'success' : 'info'">
              {{ ['目录', '菜单', '按钮'][row.menuType] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" width="140" />
        <el-table-column prop="permission" label="权限标识" width="180" />
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:menu:add'" size="small" @click="handleAdd(row)">新增子项</el-button>
            <el-button v-permission="'system:menu:edit'" size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:menu:edit'"
              size="small"
              :type="row.enabled ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button v-permission="'system:menu:delete'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingMenu.id ? '编辑菜单' : '新增菜单'" width="600px">
      <el-form :model="editingMenu" label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="editingMenu.parentId"
            :data="menuTree"
            :props="{ label: 'menuName', children: 'children', value: 'id' }"
            check-strictly
            placeholder="选择上级菜单（留空为顶级）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="editingMenu.menuType">
            <el-radio :label="0">目录</el-radio>
            <el-radio :label="1">菜单</el-radio>
            <el-radio :label="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称"><el-input v-model="editingMenu.menuName" /></el-form-item>
        <el-form-item v-if="editingMenu.menuType !== 2" label="路由路径"><el-input v-model="editingMenu.path" /></el-form-item>
        <el-form-item v-if="editingMenu.menuType === 1" label="组件路径"><el-input v-model="editingMenu.component" /></el-form-item>
        <el-form-item label="权限标识"><el-input v-model="editingMenu.permission" placeholder="如: system:user:list" /></el-form-item>
        <el-form-item v-if="editingMenu.menuType !== 2" label="图标"><el-input v-model="editingMenu.icon" placeholder="Element Plus 图标名" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editingMenu.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMenuTree, createMenu, updateMenu, toggleMenuStatus, deleteMenu } from '@/api/system'
import type { MenuInfo, MenuRequest } from '@/types'

const loading = ref(false)
const menuTree = ref<MenuInfo[]>([])
const dialogVisible = ref(false)
const searchForm = reactive({
  keyword: '',
  menuType: undefined as number | undefined,
  enabled: undefined as boolean | undefined,
})
const editingMenu = reactive<Partial<MenuInfo>>({
  menuType: 1,
  parentId: 0,
  sortOrder: 0
})

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, any> = {}
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.menuType !== undefined) params.menuType = searchForm.menuType
    if (searchForm.enabled !== undefined) params.enabled = searchForm.enabled
    const res = await getMenuTree(Object.keys(params).length > 0 ? params : undefined)
    menuTree.value = res?.data || []
  } catch { menuTree.value = [] }
  finally { loading.value = false }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.menuType = undefined
  searchForm.enabled = undefined
  loadData()
}

function handleAdd(parent: MenuInfo | null) {
  Object.assign(editingMenu, {
    id: undefined, menuName: '', path: '', component: '', permission: '',
    icon: '', menuType: 1, parentId: parent?.id || 0, sortOrder: 0
  })
  dialogVisible.value = true
}

function handleEdit(row: MenuInfo) {
  Object.assign(editingMenu, row)
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (editingMenu.id) {
      await updateMenu(editingMenu.id, editingMenu as MenuRequest)
    } else {
      await createMenu(editingMenu as MenuRequest)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { /* */ }
}

async function handleToggle(row: MenuInfo) {
  try {
    await toggleMenuStatus(row.id, !row.enabled)
    ElMessage.success('操作成功')
    loadData()
  } catch { /* */ }
}

async function handleDelete(row: MenuInfo) {
  try {
    await ElMessageBox.confirm(`确定删除菜单 "${row.menuName}" 吗？`, '提示', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* */ }
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
