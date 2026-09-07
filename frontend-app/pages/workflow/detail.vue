<template>
  <view class="page">
    <view class="card" v-if="detail">
      <view class="info-section">
        <text class="text-lg text-bold">{{ detail.title }}</text>
        <view :class="['status-tag', statusClass(detail.status)]">{{ statusText(detail.status) }}</view>
      </view>
      <view class="info-row"><text class="info-label">申请人</text><text class="info-value">{{ detail.applicant }}</text></view>
      <view class="info-row"><text class="info-label">创建时间</text><text class="info-value">{{ detail.createdAt || '-' }}</text></view>
      <view class="info-row"><text class="info-label">当前节点</text><text class="info-value">{{ detail.currentNodeName || '-' }}</text></view>
      <view class="info-row" v-if="detail.applyContent"><text class="info-label">申请内容</text><text class="info-value">{{ detail.applyContent }}</text></view>
    </view>

    <view class="card" v-if="detail && detail.tasks && detail.tasks.length">
      <text class="text-bold text-lg mb-20">审批记录</text>
      <view class="timeline">
        <view class="timeline-item" v-for="task in detail.tasks" :key="task.id">
          <view :class="['timeline-dot', taskStatusClass(task.status)]"></view>
          <view class="timeline-content">
            <view class="flex-between">
              <text class="text-bold">{{ task.nodeName }}</text>
              <text :class="['text-sm', taskStatusTextClass(task.status)]">{{ taskStatusText(task.status) }}</text>
            </view>
            <text class="text-sm text-info">审批人: {{ task.approver }}</text>
            <text class="text-sm text-info" v-if="task.createdAt">{{ task.createdAt }}</text>
            <view class="mt-10" v-if="task.comment"><text class="text-sm">{{ task.comment }}</text></view>
          </view>
        </view>
      </view>
    </view>

    <view class="card" v-if="canApprove">
      <text class="text-bold text-lg mb-20">审批操作</text>
      <textarea v-model="approvalForm.comment" class="form-textarea" placeholder="请输入审批意见" />
      <view class="btn-group">
        <button class="btn btn-success" @tap="handleApprove('APPROVE')">通过</button>
        <button class="btn btn-danger" @tap="handleApprove('REJECT')">驳回</button>
        <button class="btn btn-warning" @tap="handleApprove('TRANSFER')">转交</button>
      </view>
      <view class="mt-20" v-if="approvalForm.action === 'TRANSFER'">
        <input v-model="approvalForm.transferTo" class="form-input" placeholder="转交人用户名" />
        <button class="btn btn-primary mt-10" @tap="confirmTransfer">确认转交</button>
      </view>
    </view>
  </view>
</template>

<script>
import { ref, reactive, computed } from 'vue'
import { getWorkflowDetail, approveWorkflow } from '@/api/workflow.js'
import { useUserStore } from '@/stores/user.js'

export default {
  setup() {
    const userStore = useUserStore()
    const detail = ref(null)
    const approvalForm = reactive({ taskId: 0, action: '', comment: '', transferTo: '' })

    const canApprove = computed(() => {
      if (!detail.value || detail.value.status !== 'PENDING') return false
      const pendingTask = detail.value.tasks?.find(t => t.status === 'PENDING' && t.approver === userStore.username)
      if (pendingTask) {
        approvalForm.taskId = pendingTask.id
        return true
      }
      return false
    })

    function statusText(status) {
      return { PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[status] || status
    }
    function statusClass(status) {
      return { PENDING: 'status-warning', APPROVED: 'status-success', REJECTED: 'status-danger', WITHDRAWN: 'status-info' }[status] || 'status-info'
    }
    function taskStatusText(status) {
      return { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回', TRANSFERRED: '已转交' }[status] || status
    }
    function taskStatusTextClass(status) {
      return { PENDING: 'text-warning', APPROVED: 'text-success', REJECTED: 'text-danger', TRANSFERRED: 'text-primary' }[status] || 'text-info'
    }
    function taskStatusClass(status) {
      return { PENDING: 'dot-warning', APPROVED: 'dot-success', REJECTED: 'dot-danger', TRANSFERRED: 'dot-primary' }[status] || 'dot-info'
    }

    async function loadData(id) {
      try {
        const res = await getWorkflowDetail(id)
        detail.value = res?.data || null
      } catch (e) {
        uni.showToast({ title: '加载失败', icon: 'none' })
      }
    }

    async function handleApprove(action) {
      approvalForm.action = action
      if (action !== 'TRANSFER') {
        await doApprove()
      }
    }

    async function confirmTransfer() {
      if (!approvalForm.transferTo) {
        uni.showToast({ title: '请输入转交人', icon: 'none' })
        return
      }
      await doApprove()
    }

    async function doApprove() {
      try {
        await approveWorkflow({
          taskId: approvalForm.taskId,
          action: approvalForm.action,
          comment: approvalForm.comment,
          transferTo: approvalForm.transferTo || undefined
        })
        uni.showToast({ title: '操作成功', icon: 'success' })
        if (detail.value?.id) loadData(detail.value.id)
      } catch (e) {
        uni.showToast({ title: '操作失败', icon: 'none' })
      }
    }

    return {
      userStore, detail, approvalForm, canApprove,
      statusText, statusClass, taskStatusText, taskStatusTextClass, taskStatusClass,
      loadData, handleApprove, confirmTransfer
    }
  },
  onLoad(options) {
    const id = Number(options.id)
    if (id) this.loadData(id)
  }
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; padding-bottom: 40rpx; }
.info-section { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.info-row { display: flex; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.info-row:last-child { border-bottom: none; }
.info-label { color: #909399; font-size: 28rpx; }
.info-value { color: #303133; font-size: 28rpx; text-align: right; flex: 1; margin-left: 20rpx; }
.status-tag { padding: 4rpx 16rpx; border-radius: 8rpx; font-size: 22rpx; }
.status-success { background: #f0f9eb; color: #67c23a; }
.status-warning { background: #fdf6ec; color: #e6a23c; }
.status-danger { background: #fef0f0; color: #f56c6c; }
.status-info { background: #f4f4f5; color: #909399; }
.timeline { padding-left: 20rpx; }
.timeline-item { position: relative; padding: 16rpx 0 16rpx 40rpx; border-left: 2rpx solid #e4e7ed; }
.timeline-item:last-child { border-left-color: transparent; }
.timeline-dot { position: absolute; left: -10rpx; top: 24rpx; width: 18rpx; height: 18rpx; border-radius: 50%; }
.dot-success { background: #67c23a; }
.dot-warning { background: #e6a23c; }
.dot-danger { background: #f56c6c; }
.dot-primary { background: #409eff; }
.dot-info { background: #909399; }
.timeline-content { display: flex; flex-direction: column; gap: 4rpx; }
.form-textarea { width: 100%; height: 160rpx; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 16rpx; font-size: 28rpx; box-sizing: border-box; }
.form-input { width: 100%; height: 80rpx; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; box-sizing: border-box; }
.btn-group { display: flex; gap: 16rpx; margin-top: 20rpx; }
.btn { flex: 1; height: 76rpx; line-height: 76rpx; border: none; border-radius: 12rpx; font-size: 28rpx; color: #fff; }
.btn::after { border: none; }
.btn-success { background: #67c23a; }
.btn-danger { background: #f56c6c; }
.btn-warning { background: #e6a23c; }
.btn-primary { background: #409eff; }
</style>
