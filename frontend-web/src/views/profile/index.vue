<template>
  <div class="app-container">
    <!-- 个人资料卡片 -->
    <el-card shadow="never" class="profile-header-card">
      <div class="profile-header">
        <div class="avatar-section">
          <el-upload
            class="avatar-uploader"
            :show-file-list="false"
            :before-upload="handleAvatarUpload"
            accept="image/*"
          >
            <el-avatar :size="80" class="profile-avatar">
              <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" />
              <span v-else class="avatar-text">{{ avatarText }}</span>
            </el-avatar>
            <div class="avatar-overlay">
              <el-icon><Camera /></el-icon>
              <span>更换头像</span>
            </div>
          </el-upload>
        </div>
        <div class="user-meta">
          <h2 class="user-display-name">{{ profile.nickname || profile.username || '-' }}</h2>
          <p class="user-role">
            <el-tag size="small" :type="profile.userType === 'admin' ? 'danger' : 'info'">
              {{ profile.userType === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
            <span class="meta-item">账号: {{ profile.username }}</span>
          </p>
          <p class="meta-info">
            <span class="meta-item">邮箱: {{ profile.email || '未设置' }}</span>
            <span class="meta-item">手机: {{ profile.phone || '未设置' }}</span>
          </p>
          <p class="meta-info" v-if="profile.lastLoginTime">
            <span class="meta-item">最后登录: {{ profile.lastLoginTime }}</span>
            <span class="meta-item" v-if="profile.lastLoginIp">IP: {{ profile.lastLoginIp }}</span>
          </p>
        </div>
      </div>
    </el-card>

    <!-- Tab 切换 -->
    <el-card shadow="never" class="profile-tabs-card">
      <el-tabs v-model="activeTab">
        <!-- 基本资料 -->
        <el-tab-pane label="基本资料" name="info">
          <el-form :model="profile" label-width="100px" size="default" class="profile-form">
            <el-form-item label="用户名">
              <el-input :model-value="profile.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profile.nickname" placeholder="请输入昵称" maxlength="50" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profile.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profile.phone" placeholder="请输入手机号" maxlength="20" />
            </el-form-item>
            <el-form-item label="用户类型">
              <el-tag>{{ profile.userType === 'admin' ? '管理员' : '普通用户' }}</el-tag>
            </el-form-item>
            <el-form-item label="租户">
              <span>{{ profile.tenantId || 'default' }}</span>
            </el-form-item>
            <el-form-item label="注册时间" v-if="profile.createTime">
              <span>{{ profile.createTime }}</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">
                保存资料
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 修改密码 -->
        <el-tab-pane label="修改密码" name="password">
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" size="default" class="profile-form">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码（6-32位）" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPwd" @click="handleChangePassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { getProfile, updateProfile, changePassword } from '@/api/profile'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const activeTab = ref('info')
const savingProfile = ref(false)
const changingPwd = ref(false)
const pwdFormRef = ref<FormInstance>()

const avatarUrl = ref('')
const profile = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  userType: '',
  tenantId: '',
  createTime: '',
  lastLoginTime: '',
  lastLoginIp: '',
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const avatarText = computed(() => {
  const name = profile.nickname || profile.username
  return name ? name.charAt(0).toUpperCase() : 'U'
})

const validateConfirmPwd = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度6-32位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPwd, trigger: 'blur' },
  ],
}

onMounted(async () => {
  try {
    const res = await getProfile()
    if (res?.data) {
      Object.assign(profile, res.data)
      if (res.data.avatar) avatarUrl.value = res.data.avatar
    }
  } catch { /* handled by interceptor */ }
})

/** 头像上传 */
function handleAvatarUpload(file: File) {
  const reader = new FileReader()
  reader.onload = async (e) => {
    const base64 = e.target?.result as string
    avatarUrl.value = base64
    try {
      await updateProfile({ avatar: base64 })
      ElMessage.success('头像更新成功')
    } catch { /* */ }
  }
  reader.readAsDataURL(file)
  return false
}

/** 保存个人资料 */
async function handleSaveProfile() {
  savingProfile.value = true
  try {
    await updateProfile({
      nickname: profile.nickname,
      email: profile.email || undefined,
      phone: profile.phone || undefined,
    })
    // 同步更新 store
    userStore.userInfo = {
      ...userStore.userInfo,
      nickname: profile.nickname,
    } as any
    ElMessage.success('资料保存成功')
  } catch { /* */ } finally {
    savingProfile.value = false
  }
}

/** 修改密码 */
async function handleChangePassword() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    changingPwd.value = true
    try {
      const res = await changePassword({
        oldPassword: pwdForm.oldPassword,
        newPassword: pwdForm.newPassword,
      })
      if ((res as any)?.code === 200) {
        ElMessage.success('密码修改成功，请重新登录')
        pwdForm.oldPassword = ''
        pwdForm.newPassword = ''
        pwdForm.confirmPassword = ''
        setTimeout(() => {
          userStore.logout()
          window.location.href = '/adminLogin'
        }, 1500)
      }
    } catch { /* */ } finally {
      changingPwd.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.profile-header-card {
  margin-bottom: 16px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-section {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
}

.profile-avatar {
  border: 3px solid #e8e8e8;
}

.avatar-text {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.3s;
}

.avatar-section:hover .avatar-overlay {
  opacity: 1;
}

.user-meta {
  flex: 1;
  min-width: 0;
}

.user-display-name {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.user-role {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 6px;
}

.meta-info {
  display: flex;
  gap: 24px;
  margin: 4px 0 0;
}

.meta-item {
  font-size: 13px;
  color: #909399;
}

.profile-tabs-card {
  :deep(.el-tabs__header) {
    margin-bottom: 8px;
  }
}

.profile-form {
  max-width: 480px;
  padding-top: 8px;
}
</style>
