<template>
  <view class="page">
    <view class="card">
      <text class="text-bold text-lg mb-20">发起审批申请</text>

      <view class="form-group">
        <text class="form-label">审批流程</text>
        <picker :range="definitionNames" @change="onDefChange">
          <view class="form-picker">{{ form.definitionKey ? definitionNames[defIndex] : '请选择审批流程' }}</view>
        </picker>
      </view>

      <view class="form-group">
        <text class="form-label">申请标题</text>
        <input v-model="form.title" class="form-input" placeholder="请输入标题" />
      </view>

      <view class="form-group">
        <text class="form-label">申请内容</text>
        <textarea v-model="form.applyContent" class="form-textarea" placeholder="详细描述你的申请内容" />
      </view>

      <view class="form-group">
        <text class="form-label">申请人</text>
        <input :value="form.applicant" class="form-input" disabled />
      </view>

      <button class="submit-btn" @tap="handleSubmit" :loading="submitting">提交申请</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getWorkflowDefinitions, submitWorkflow } from '@/api/workflow.js'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()
const submitting = ref(false)
const definitions = ref([])
const defIndex = ref(0)

const form = reactive({
  definitionKey: '',
  title: '',
  applyContent: '',
  applicant: userStore.username || 'admin'
})

const definitionNames = computed(() => definitions.value.map(d => d.definitionName || d.definitionKey))

function onDefChange(e) {
  defIndex.value = e.detail.value
  form.definitionKey = definitions.value[defIndex.value]?.definitionKey || ''
}

async function loadDefinitions() {
  try {
    const res = await getWorkflowDefinitions()
    definitions.value = res?.data || []
  } catch (e) {
    definitions.value = []
  }
}

async function handleSubmit() {
  if (!form.definitionKey) {
    uni.showToast({ title: '请选择审批流程', icon: 'none' })
    return
  }
  if (!form.title) {
    uni.showToast({ title: '请输入标题', icon: 'none' })
    return
  }
  if (!form.applyContent) {
    uni.showToast({ title: '请输入申请内容', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    await submitWorkflow(form)
    uni.showToast({ title: '申请已提交', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e) {
    uni.showToast({ title: '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onMounted(loadDefinitions)
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; }
.form-group { margin-bottom: 24rpx; }
.form-label { display: block; font-size: 28rpx; color: #606266; margin-bottom: 10rpx; }
.form-input { width: 100%; height: 80rpx; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; box-sizing: border-box; }
.form-textarea { width: 100%; height: 200rpx; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 16rpx; font-size: 28rpx; box-sizing: border-box; }
.form-picker { width: 100%; height: 80rpx; line-height: 80rpx; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
.submit-btn { width: 100%; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; border: none; border-radius: 12rpx; font-size: 32rpx; margin-top: 20rpx; }
.submit-btn::after { border: none; }
</style>
