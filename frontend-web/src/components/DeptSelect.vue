<template>
  <el-tree-select
    v-model="selected"
    :data="deptTree"
    :props="treeProps"
    :multiple="multiple"
    :clearable="clearable"
    :placeholder="placeholder"
    :render-after-expand="false"
    filterable
    check-strictly
    style="width: 100%"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDeptTree } from '@/api/system'
import type { DeptInfo } from '@/types'

const props = withDefaults(defineProps<{
  modelValue: number | number[]
  multiple?: boolean
  clearable?: boolean
  placeholder?: string
}>(), {
  multiple: true,
  clearable: true,
  placeholder: '请选择部门'
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: number | number[]): void
}>()

const selected = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val as number | number[])
})

const deptTree = ref<DeptInfo[]>([])
const loading = ref(false)
const treeProps = {
  label: 'deptName',
  value: 'id',
  children: 'children'
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDeptTree()
    deptTree.value = res.data || []
  } catch {
    ElMessage.error('部门树加载失败')
  } finally {
    loading.value = false
  }
})
</script>
