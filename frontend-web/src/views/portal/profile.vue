<template>
  <div class="portal-page">
    <div class="page-header">
      <el-page-header @back="router.back()" title="返回" content="个人中心" />
    </div>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover" class="profile-card">
          <div class="profile-avatar-section">
            <el-avatar :size="80" :icon="UserFilled" class="avatar" />
            <h3>{{ userStore.nickname }}</h3>
            <el-tag :type="userStore.isAdmin ? 'danger' : 'success'" effect="light">
              {{ userStore.isAdmin ? '管理员' : '普通用户' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="hover">
          <template #header><span>基本信息</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名">{{ userStore.userInfo?.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ userStore.userInfo?.nickname }}</el-descriptions-item>
            <el-descriptions-item label="用户ID">{{ userStore.userInfo?.id }}</el-descriptions-item>
            <el-descriptions-item label="用户类型">{{ userStore.userInfo?.userType }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ userStore.userInfo?.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="租户">{{ userStore.userInfo?.tenantId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色" :span="2">
              <el-tag
                v-for="role in userStore.userInfo?.roleCodes"
                :key="role" size="small" type="info" effect="plain"
                style="margin-right: 6px;"
              >{{ role }}</el-tag>
              <span v-if="!userStore.userInfo?.roleCodes?.length">-</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { computed } from 'vue'

const router = useRouter()
const userStore = useUserStore()

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')
</script>

<style scoped lang="scss">
.portal-page { max-width: 1000px; margin: 0 auto; }

.page-header { margin-bottom: 20px; }

.profile-card {
  text-align: center;

  .profile-avatar-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 16px 0;

    .avatar {
      background: linear-gradient(135deg, #409eff, #1677ff);
      font-size: 32px;
    }
    h3 { margin: 0; font-size: 18px; }
  }
}
</style>
