import { get, post, put } from '@/utils/request'
import type {
  ApiResponse, ChangePasswordRequest, UpdateProfileRequest,
  FeedbackRequest, FeedbackRecord, VersionInfo, UserRegisterResponse
} from '@/types'

const SYSTEM_BASE = '/system-server'

// ==================== 个人中心 (已迁移至 system-server) ====================

/** 获取当前用户详细资料 */
export function getProfile(): Promise<ApiResponse<any>> {
  return get(`${SYSTEM_BASE}/api/user/profile`)
}

/** 更新个人资料 */
export function updateProfile(data: UpdateProfileRequest): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/user/profile`, data)
}

/** 修改密码 */
export function changePassword(data: ChangePasswordRequest): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/user/profile/password`, data)
}

// ==================== 意见反馈 (已迁移至 system-server) ====================

/** 提交意见反馈 */
export function submitFeedback(data: FeedbackRequest): Promise<ApiResponse<void>> {
  return post(`${SYSTEM_BASE}/api/feedback`, data)
}

/** 获取我的反馈列表 */
export function getMyFeedback(): Promise<ApiResponse<FeedbackRecord[]>> {
  return get(`${SYSTEM_BASE}/api/feedback`)
}

// ==================== 版本信息 (已迁移至 system-server) ====================

/** 获取版本信息 */
export function getVersionInfo(): Promise<ApiResponse<VersionInfo>> {
  return get(`${SYSTEM_BASE}/api/feedback/version`)
}
