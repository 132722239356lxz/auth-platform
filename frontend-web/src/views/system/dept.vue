<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="searchForm.keyword"
          placeholder="部门名称 / 编码 / 负责人"
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
        <el-button v-permission="'system:dept:add'" type="success" @click="handleAdd(null)">新增部门</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table
        :data="deptTree"
        v-loading="loading"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="180" />
        <el-table-column prop="deptCode" label="部门编码" width="160" />
        <el-table-column prop="leader" label="负责人" width="100" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:add'" size="small" @click="handleAdd(row)">新增子部门</el-button>
            <el-button v-permission="'system:dept:edit'" size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:dept:edit'"
              size="small"
              :type="row.enabled ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button v-permission="'system:dept:delete'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingDept.id ? '编辑部门' : '新增部门'" width="600px">
      <el-form :model="editingDept" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="editingDept.parentId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            check-strictly
            placeholder="选择上级部门（留空为顶级）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input v-model="editingDept.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="editingDept.deptCode" :disabled="!!editingDept.id" placeholder="唯一编码，如 DEPT_TECH" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="editingDept.leader" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="editingDept.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editingDept.email" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editingDept.sortOrder" :min="0" />
        </el-form-item>
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
import { getDeptTree, createDept, updateDept, toggleDeptStatus, deleteDept } from '@/api/system'
import type { DeptInfo, DeptRequest } from '@/types'

const loading = ref(false)
const deptTree = ref<DeptInfo[]>([])
const dialogVisible = ref(false)
const searchForm = reactive({
  keyword: '',
  enabled: undefined as boolean | undefined,
})
const editingDept = reactive<Partial<DeptRequest & { id?: number }>>({
  parentId: 0,
  sortOrder: 0,
  enabled: true,
})

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, any> = {}
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.enabled !== undefined) params.enabled = searchForm.enabled
    const res = await getDeptTree(Object.keys(params).length > 0 ? params : undefined)
    deptTree.value = res?.data || []
  } catch { deptTree.value = [] }
  finally { loading.value = false }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.enabled = undefined
  loadData()
}

function handleAdd(parent: DeptInfo | null) {
  Object.assign(editingDept, {
    id: undefined,
    parentId: parent?.id || 0,
    deptName: '',
    deptCode: '',
    leader: '',
    phone: '',
    email: '',
    sortOrder: 0,
    enabled: true,
  })
  dialogVisible.value = true
}

function handleEdit(row: DeptInfo) {
  Object.assign(editingDept, {
    id: row.id,
    parentId: row.parentId || 0,
    deptName: row.deptName,
    deptCode: row.deptCode,
    leader: row.leader || '',
    phone: row.phone || '',
    email: row.email || '',
    sortOrder: row.sortOrder || 0,
    enabled: row.enabled,
  })
  dialogVisible.value = true
}

async function handleSave() {
  try {
    const data: DeptRequest = {
      deptName: editingDept.deptName || '',
      deptCode: editingDept.deptCode || '',
      parentId: editingDept.parentId || 0,
      leader: editingDept.leader,
      phone: editingDept.phone,
      email: editingDept.email,
      sortOrder: editingDept.sortOrder || 0,
      enabled: editingDept.enabled ?? true,
    }
    if (editingDept.id) {
      await updateDept(editingDept.id, data)
    } else {
      await createDept(data)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { /* */ }
}

async function handleToggle(row: DeptInfo) {
  try {
    await toggleDeptStatus(row.id, !row.enabled)
    ElMessage.success('操作成功')
    loadData()
  } catch { /* */ }
}

async function handleDelete(row: DeptInfo) {
  try {
    await ElMessageBox.confirm(`确定删除部门 "${row.deptName}" 吗？`, '提示', { type: 'warning' })
    await deleteDept(row.id)
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
