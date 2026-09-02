<template>
  <el-select
    v-model="selected"
    :multiple="multiple"
    :clearable="clearable"
    :placeholder="placeholder"
    :loading="loading"
    filterable
    style="width: 100%"
  >
    <el-option
      v-for="user in enabledUsers"
      :key="user.username"
      :label="formatLabel(user)"
      :value="user.username"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers } from '@/api/system'
import type { UserInfo } from '@/types'

const props = withDefaults(defineProps<{
  modelValue: string | string[]
  multiple?: boolean
  clearable?: boolean
  placeholder?: string
}>(), {
  multiple: true,
  clearable: true,
  placeholder: '请选择用户'
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: string | string[]): void
}>()

const selected = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const users = ref<UserInfo[]>([])
const loading = ref(false)

const enabledUsers = computed(() => users.value.filter(u => u.enabled !== false))

function formatLabel(user: UserInfo) {
  return user.nickname && user.nickname !== user.username
    ? `${user.nickname}(${user.username})`
    : user.username
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getUsers()
    users.value = res.data || []
  } catch {
    ElMessage.error('用户列表加载失败')
  } finally {
    loading.value = false
  }
})
</script>
