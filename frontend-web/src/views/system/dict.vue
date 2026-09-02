<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="typeQuery.keyword"
          placeholder="字典名称 / 类型标识"
          clearable
          style="width: 220px"
          @keyup.enter="loadTypes"
        />
        <el-select v-model="typeQuery.enabled" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-button type="primary" @click="loadTypes">查询</el-button>
        <el-button @click="resetTypeQuery">重置</el-button>
        <el-button v-permission="'system:dict:add'" type="success" @click="handleAddType">新增字典类型</el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <!-- 左侧：字典类型 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="type-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">字典类型</span>
            </div>
          </template>
          <el-table
            :data="filteredTypeList"
            v-loading="typeLoading"
            border
            stripe
            highlight-current-row
            style="width: 100%"
            @row-click="handleTypeRowClick"
          >
            <el-table-column prop="dictName" label="字典名称" min-width="120" />
            <el-table-column prop="dictType" label="类型标识" min-width="130" show-overflow-tooltip />
            <el-table-column prop="enabled" label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:edit'" size="small" @click.stop="handleEditType(row)">编辑</el-button>
                <el-button v-permission="'system:dict:delete'" size="small" type="danger" @click.stop="handleDeleteType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：字典数据 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="data-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                字典数据
                <span v-if="currentType" style="font-weight: 400; color: #409eff">
                  - {{ currentType.dictName }} ({{ currentType.dictType }})
                </span>
              </span>
              <el-button
                v-permission="'system:dict:add'"
                type="primary"
                :disabled="!currentType"
                @click="handleAddData"
              >新增数据</el-button>
            </div>
          </template>
          <el-table :data="dataList" v-loading="dataLoading" border stripe style="width: 100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="dictLabel" label="标签" width="120" />
            <el-table-column prop="dictValue" label="值" width="120" />
            <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
            <el-table-column prop="cssClass" label="样式" width="100" />
            <el-table-column prop="listClass" label="列表样式" width="100" />
            <el-table-column prop="enabled" label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:edit'" size="small" @click="handleEditData(row)">编辑</el-button>
                <el-button v-permission="'system:dict:delete'" size="small" type="danger" @click="handleDeleteData(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型弹窗 -->
    <el-dialog v-model="typeDialogVisible" :title="isEditType ? '编辑字典类型' : '新增字典类型'" width="520px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeFormRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="类型标识" prop="dictType">
          <el-input v-model="typeForm.dictType" placeholder="如 sys_user_status" :disabled="isEditType" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="typeForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSaving" @click="handleSaveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据弹窗 -->
    <el-dialog v-model="dataDialogVisible" :title="isEditData ? '编辑字典数据' : '新增字典数据'" width="520px">
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataFormRules" label-width="90px">
        <el-form-item label="所属类型">
          <el-input :value="currentType ? currentType.dictName : ''" disabled />
        </el-form-item>
        <el-form-item label="标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="如 启用" />
        </el-form-item>
        <el-form-item label="值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="如 1" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="样式类">
          <el-input v-model="dataForm.cssClass" placeholder="如 success" />
        </el-form-item>
        <el-form-item label="列表样式">
          <el-input v-model="dataForm.listClass" placeholder="如 default" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataSaving" @click="handleSaveData">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getDictTypes, getDictDataByType, createDictType, updateDictType, deleteDictType,
  createDictData, updateDictData, deleteDictData
} from '@/api/system'
import type { DictTypeInfo, DictDataInfo } from '@/types'

const typeLoading = ref(false)
const dataLoading = ref(false)
const typeList = ref<DictTypeInfo[]>([])
const dataList = ref<DictDataInfo[]>([])
const currentType = ref<DictTypeInfo | null>(null)

const typeQuery = reactive({ keyword: '', enabled: undefined as boolean | undefined })

const filteredTypeList = computed(() => {
  return typeList.value.filter(t => {
    if (typeQuery.enabled !== undefined && t.enabled !== typeQuery.enabled) return false
    if (typeQuery.keyword) {
      const k = typeQuery.keyword.toLowerCase()
      return t.dictName.toLowerCase().includes(k) || t.dictType.toLowerCase().includes(k)
    }
    return true
  })
})

const typeDialogVisible = ref(false)
const typeSaving = ref(false)
const isEditType = ref(false)
const editTypeId = ref<number | null>(null)
const typeFormRef = ref<FormInstance>()
const typeForm = reactive({ dictName: '', dictType: '', description: '' })
const typeFormRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入类型标识', trigger: 'blur' }],
}

const dataDialogVisible = ref(false)
const dataSaving = ref(false)
const isEditData = ref(false)
const editDataId = ref<number | null>(null)
const dataFormRef = ref<FormInstance>()
const dataForm = reactive({
  dictLabel: '', dictValue: '', sortOrder: 0, cssClass: '', listClass: '', remark: ''
})
const dataFormRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
}

async function loadTypes() {
  typeLoading.value = true
  try {
    const res = await getDictTypes({
      keyword: typeQuery.keyword || undefined,
      enabled: typeQuery.enabled,
    })
    typeList.value = res?.data || []
  } catch {
    typeList.value = []
  } finally {
    typeLoading.value = false
  }
}

async function loadDataList(typeId?: number) {
  if (!typeId) {
    dataList.value = []
    return
  }
  dataLoading.value = true
  try {
    const res = await getDictDataByType(typeId)
    dataList.value = res?.data || []
  } catch {
    dataList.value = []
  } finally {
    dataLoading.value = false
  }
}

function resetTypeQuery() {
  typeQuery.keyword = ''
  typeQuery.enabled = undefined
  loadTypes()
}

function handleTypeRowClick(row: DictTypeInfo) {
  currentType.value = row
  loadDataList(row.id)
}

function handleAddType() {
  isEditType.value = false
  editTypeId.value = null
  typeForm.dictName = ''
  typeForm.dictType = ''
  typeForm.description = ''
  typeDialogVisible.value = true
}

function handleEditType(row: DictTypeInfo) {
  isEditType.value = true
  editTypeId.value = row.id
  typeForm.dictName = row.dictName
  typeForm.dictType = row.dictType
  typeForm.description = row.description || ''
  typeDialogVisible.value = true
}

async function handleSaveType() {
  const valid = await typeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  typeSaving.value = true
  try {
    if (isEditType.value && editTypeId.value) {
      await updateDictType(editTypeId.value, { ...typeForm })
      ElMessage.success('字典类型更新成功')
    } else {
      await createDictType({ ...typeForm })
      ElMessage.success('字典类型创建成功')
    }
    typeDialogVisible.value = false
    loadTypes()
  } catch { /* */ }
  finally { typeSaving.value = false }
}

async function handleDeleteType(row: DictTypeInfo) {
  try {
    await ElMessageBox.confirm(`删除字典类型 "${row.dictName}" 将同时删除其下所有数据，确定吗？`, '提示', { type: 'warning' })
    await deleteDictType(row.id)
    ElMessage.success('删除成功')
    if (currentType.value?.id === row.id) {
      currentType.value = null
      dataList.value = []
    }
    loadTypes()
  } catch { /* cancelled */ }
}

function handleAddData() {
  if (!currentType.value) return
  isEditData.value = false
  editDataId.value = null
  dataForm.dictLabel = ''
  dataForm.dictValue = ''
  dataForm.sortOrder = 0
  dataForm.cssClass = ''
  dataForm.listClass = ''
  dataForm.remark = ''
  dataDialogVisible.value = true
}

function handleEditData(row: DictDataInfo) {
  isEditData.value = true
  editDataId.value = row.id
  dataForm.dictLabel = row.dictLabel
  dataForm.dictValue = row.dictValue
  dataForm.sortOrder = row.sortOrder || 0
  dataForm.cssClass = row.cssClass || ''
  dataForm.listClass = row.listClass || ''
  dataForm.remark = row.remark || ''
  dataDialogVisible.value = true
}

async function handleSaveData() {
  if (!currentType.value) return
  const valid = await dataFormRef.value?.validate().catch(() => false)
  if (!valid) return
  dataSaving.value = true
  try {
    const payload = { typeId: currentType.value.id, ...dataForm }
    if (isEditData.value && editDataId.value) {
      await updateDictData(editDataId.value, payload)
      ElMessage.success('字典数据更新成功')
    } else {
      await createDictData(payload)
      ElMessage.success('字典数据创建成功')
    }
    dataDialogVisible.value = false
    loadDataList(currentType.value.id)
  } catch { /* */ }
  finally { dataSaving.value = false }
}

async function handleDeleteData(row: DictDataInfo) {
  try {
    await ElMessageBox.confirm(`确定删除字典数据 "${row.dictLabel}" 吗？`, '提示', { type: 'warning' })
    await deleteDictData(row.id)
    ElMessage.success('删除成功')
    if (currentType.value) loadDataList(currentType.value.id)
  } catch { /* cancelled */ }
}

onMounted(() => {
  loadTypes()
})
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 600; font-size: 15px; }
.type-card, .data-card { margin-bottom: 16px; }
</style>
