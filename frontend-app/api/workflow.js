import { get, post } from '@/utils/request.js'

const FLOW_BASE = '/auth-flow'

export function getWorkflowDefinitions() {
  return get(`${FLOW_BASE}/api/workflow/definition/list`)
}

export function submitWorkflow(params) {
  return post(`${FLOW_BASE}/api/workflow/submit`, null, null, params)
}

export function approveWorkflow(data) {
  return post(`${FLOW_BASE}/api/workflow/approve`, data)
}

export function getWorkflowDetail(instanceId) {
  return get(`${FLOW_BASE}/api/workflow/${instanceId}`)
}

export function getMyApplications(applicant) {
  return get(`${FLOW_BASE}/api/workflow/my-applications`, { applicant })
}

export function getMyPending(approver) {
  return get(`${FLOW_BASE}/api/workflow/my-pending`, { approver })
}
