<template>
  <div class="portal-page">
    <div class="page-header">
      <el-page-header @back="router.back()" :title="$t('common.back')" :content="$t('settings.title')" />
    </div>

    <el-row :gutter="20">
      <!-- 左侧：头像与快捷入口 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="profile-card">
          <div class="avatar-section">
            <el-avatar :size="96" :src="avatarUrl" class="avatar">
              <el-icon :size="40"><UserFilled /></el-icon>
            </el-avatar>
            <el-upload
              class="avatar-uploader"
              action=""
              :auto-upload="false"
              :show-file-list="false"
              accept="image/jpeg,image/png,image/gif"
              :before-upload="beforeAvatarUpload"
              @change="handleAvatarChange"
            >
              <el-button type="primary" link size="small">
                <el-icon><Camera /></el-icon>{{ $t('settings.avatar') }}
              </el-button>
            </el-upload>
            <h3>{{ userStore.nickname }}</h3>
            <el-tag :type="userStore.isAdmin ? 'danger' : 'success'" effect="light">
              {{ userStore.isAdmin ? $t('common.admin') : $t('common.user') }}
            </el-tag>
          </div>
          <el-divider />
          <div class="account-tips">
            <el-alert
              :title="$t('settings.securityTips.title')"
              :description="$t('settings.securityTips.description')"
              type="info"
              :closable="false"
              show-icon
            />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：设置表单 -->
      <el-col :xs="24" :md="16">
        <!-- 个人资料 -->
        <el-card shadow="hover" class="setting-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('settings.profile') }}</span>
              <el-button type="primary" @click="saveProfile" :loading="savingProfile">{{ $t('common.save') }}</el-button>
            </div>
          </template>
          <el-form :model="profileForm" ref="profileFormRef" :rules="profileRules" label-width="80px">
            <el-form-item :label="$t('settings.nickname')" prop="nickname">
              <el-input v-model="profileForm.nickname" :placeholder="$t('settings.nicknamePlaceholder')" maxlength="20" show-word-limit />
            </el-form-item>
            <el-form-item :label="$t('settings.email')" prop="email">
              <el-input v-model="profileForm.email" :placeholder="$t('settings.emailPlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('settings.phone')" prop="phone">
              <el-input v-model="profileForm.phone" :placeholder="$t('settings.phonePlaceholder')" maxlength="20" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 修改密码 -->
        <el-card shadow="hover" class="setting-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('settings.password') }}</span>
              <el-button type="primary" @click="changePassword" :loading="savingPassword">{{ $t('common.save') }}</el-button>
            </div>
          </template>
          <el-form :model="passwordForm" ref="passwordFormRef" :rules="passwordRules" label-width="100px">
            <el-form-item :label="$t('settings.oldPassword')" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" :placeholder="$t('settings.oldPasswordPlaceholder')" show-password />
            </el-form-item>
            <el-form-item :label="$t('settings.newPassword')" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" :placeholder="$t('settings.newPasswordPlaceholder')" show-password />
            </el-form-item>
            <el-form-item :label="$t('settings.confirmPassword')" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" :placeholder="$t('settings.confirmPasswordPlaceholder')" show-password />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 偏好设置（与后台管理无关） -->
        <el-card shadow="hover" class="setting-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('settings.preferences') }}</span>
            </div>
          </template>
          <el-form :model="preferenceForm" label-width="100px">
            <el-form-item :label="$t('settings.theme')">
              <el-radio-group v-model="preferenceForm.theme" @change="handleThemeChange">
                <el-radio-button label="light">{{ $t('common.light') }}</el-radio-button>
                <el-radio-button label="dark">{{ $t('common.dark') }}</el-radio-button>
                <el-radio-button label="auto">{{ $t('common.auto') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="$t('settings.notification')">
              <el-switch
                v-model="preferenceForm.notifyEnabled"
                :active-text="$t('common.open')"
                :inactive-text="$t('common.close')"
                @change="handleNotifyChange"
              />
            </el-form-item>
            <el-form-item :label="$t('settings.language')">
              <el-select v-model="preferenceForm.language" style="width: 160px" @change="handleLanguageChange">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { UserFilled, Camera } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { updateCurrentProfile, changeCurrentPassword } from '@/api/system'
import { setI18nLanguage } from '@/i18n'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const savingProfile = ref(false)
const savingPassword = ref(false)

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')

const profileForm = reactive({
  nickname: '',
  email: '',
  phone: ''
})

const profileRules: FormRules = {
  nickname: [
    { required: true, message: t('settings.nicknamePlaceholder'), trigger: 'blur' },
    { max: 20, message: t('settings.nicknameMaxLength'), trigger: 'blur' }
  ],
  email: [{ type: 'email', message: t('settings.emailInvalid'), trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$|^$/, message: t('settings.phoneInvalid'), trigger: 'blur' }]
}

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: t('settings.oldPasswordPlaceholder'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('settings.newPasswordPlaceholder'), trigger: 'blur' },
    { min: 6, message: t('settings.newPasswordMinLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('settings.confirmPasswordPlaceholder'), trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error(t('settings.confirmPasswordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const preferenceForm = reactive({
  theme: 'light',
  notifyEnabled: true,
  language: 'zh-CN'
})

onMounted(() => {
  // 初始化表单
  profileForm.nickname = userStore.userInfo?.nickname || ''
  profileForm.email = userStore.userInfo?.email || ''
  profileForm.phone = userStore.userInfo?.phone || ''

  // 读取本地偏好设置
  const prefs = localStorage.getItem('portal_preferences')
  if (prefs) {
    try {
      const parsed = JSON.parse(prefs)
      preferenceForm.theme = parsed.theme || 'light'
      preferenceForm.notifyEnabled = parsed.notifyEnabled !== false
      preferenceForm.language = parsed.language || 'zh-CN'
    } catch { /* ignore */ }
  }
  applyTheme(preferenceForm.theme)

  // 监听系统主题变化，当选择“跟随系统”时自动切换
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', onSystemThemeChange)
})

function onSystemThemeChange() {
  if (preferenceForm.theme === 'auto') {
    applyTheme('auto')
  }
}

function handleThemeChange() {
  applyTheme(preferenceForm.theme)
  persistPreferences(false)
}

function handleLanguageChange() {
  setI18nLanguage(preferenceForm.language)
  persistPreferences(false)
}

function handleNotifyChange() {
  appStore.setNotificationsEnabled(preferenceForm.notifyEnabled)
  persistPreferences(false)
}

function persistPreferences(showTip = true) {
  try {
    localStorage.setItem('portal_preferences', JSON.stringify({
      theme: preferenceForm.theme,
      notifyEnabled: preferenceForm.notifyEnabled,
      language: preferenceForm.language
    }))
    if (showTip) {
      ElMessage.success(t('settings.preferencesSaveSuccess'))
    }
  } catch (error: any) {
    ElMessage.error(error?.message || t('settings.preferencesSaveError'))
  }
}

async function saveProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingProfile.value = true
    try {
      const res = await updateCurrentProfile({
        nickname: profileForm.nickname,
        email: profileForm.email || undefined,
        phone: profileForm.phone || undefined
      })
      if (res.code === 200) {
        ElMessage.success(t('settings.profileSaveSuccess'))
        // 同步更新本地用户信息
        if (userStore.userInfo) {
          userStore.userInfo.nickname = profileForm.nickname
          userStore.userInfo.email = profileForm.email || null
          userStore.userInfo.phone = profileForm.phone || null
        }
      } else {
        ElMessage.error(res.message || t('settings.profileSaveError'))
      }
    } catch (error: any) {
      ElMessage.error(error?.message || t('settings.profileSaveError'))
    } finally {
      savingProfile.value = false
    }
  })
}

async function changePassword() {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingPassword.value = true
    try {
      const res = await changeCurrentPassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      if (res.code === 200) {
        ElMessage.success(t('settings.passwordChangeSuccess'))
        passwordForm.oldPassword = ''
        passwordForm.newPassword = ''
        passwordForm.confirmPassword = ''
        // 修改成功后退出登录
        setTimeout(() => userStore.logout(), 1500)
        setTimeout(() => router.push('/login'), 1600)
      } else {
        ElMessage.error(res.message || t('settings.passwordChangeError'))
      }
    } catch (error: any) {
      ElMessage.error(error?.message || t('settings.passwordChangeError'))
    } finally {
      savingPassword.value = false
    }
  })
}

function applyTheme(theme: string) {
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const isDark = theme === 'dark' || (theme === 'auto' && prefersDark)
  if (isDark) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

function beforeAvatarUpload(file: File) {
  const isJpgOrPng = ['image/jpeg', 'image/png', 'image/gif'].includes(file.type)
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isJpgOrPng) {
    ElMessage.error(t('settings.avatarTypeError'))
    return false
  }
  if (!isLt2M) {
    ElMessage.error(t('settings.avatarSizeError'))
    return false
  }
  return true
}

/** 压缩头像：限制最大边 200px，JPEG 质量 0.8，控制 Base64 体积 */
function compressImage(file: File, maxSize = 200, quality = 0.8): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      let width = img.width
      let height = img.height
      if (width > height) {
        if (width > maxSize) {
          height = Math.round(height * maxSize / width)
          width = maxSize
        }
      } else {
        if (height > maxSize) {
          width = Math.round(width * maxSize / height)
          height = maxSize
        }
      }
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('无法创建 canvas 上下文'))
        return
      }
      ctx.drawImage(img, 0, 0, width, height)
      resolve(canvas.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片加载失败'))
    }
    img.src = url
  })
}

async function handleAvatarChange(file: any) {
  const raw = file?.raw as File
  if (!raw) return
  if (!beforeAvatarUpload(raw)) return

  try {
    const base64 = await compressImage(raw)
    const res = await updateCurrentProfile({ avatar: base64 })
    if (res.code === 200 && res.data) {
      ElMessage.success(t('settings.avatarUploadSuccess'))
      if (userStore.userInfo) {
        userStore.userInfo.avatar = base64
      }
    } else {
      ElMessage.error(res.message || t('settings.avatarUploadError'))
    }
  } catch (error: any) {
    ElMessage.error(error?.message || t('settings.avatarUploadError'))
  }
}
</script>

<style scoped lang="scss">
.portal-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.profile-card {
  .avatar-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 16px 0;

    .avatar {
      background: linear-gradient(135deg, #409eff, #1677ff);
      font-size: 40px;
      border: 3px solid #e6f2ff;
    }

    h3 {
      margin: 0;
      font-size: 18px;
    }
  }

  .account-tips {
    margin-top: 8px;
  }
}

.setting-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
  }
}

.avatar-uploader {
  :deep(.el-upload) {
    display: inline-block;
  }
}
</style>
