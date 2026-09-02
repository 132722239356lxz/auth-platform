import { get, post, put, del } from '@/utils/request'
import type { R, WorkflowCreateRequest, WorkflowDefinition, WorkflowInstance, WorkflowForm, ApprovalRequest, DefinitionDetail, MyApprovalRecord, WorkflowDetail } from '@/types'

const FLOW_BASE = '/auth-flow'

// ==================== 工作流定义 ====================

export function createWorkflowDefinition(data: WorkflowCreateRequest): Promise<R<WorkflowDefinition>> {
  return post(`${FLOW_BASE}/api/workflow/definition`, data)
}

export function getWorkflowDefinitions(): Promise<R<WorkflowDefinition[]>> {
  return get(`${FLOW_BASE}/api/workflow/definition/list`)
}

// ==================== 申请与审批 ====================

export function submitWorkflow(params: {
  definitionKey: string; title: string; applyContent?: string; applicant: string
}): Promise<R<WorkflowInstance>> {
  return post(`${FLOW_BASE}/api/workflow/submit`, null, { params })
}

export function approveWorkflow(data: ApprovalRequest): Promise<R<void>> {
  return post(`${FLOW_BASE}/api/workflow/approve`, data)
}

export function getWorkflowDetail(instanceId: number): Promise<R<WorkflowDetail>> {
  return get(`${FLOW_BASE}/api/workflow/${instanceId}`)
}

export function getMyApplications(applicant: string): Promise<R<WorkflowInstance[]>> {
  return get(`${FLOW_BASE}/api/workflow/my-applications`, { applicant })
}
export function getMyPending(approver: string): Promise<R<WorkflowInstance[]>> {
  return get(`${FLOW_BASE}/api/workflow/my-pending`, { approver })
}

export function getMyRecords(approver: string): Promise<R<MyApprovalRecord[]>> {
  return get(`${FLOW_BASE}/api/workflow/my-records`, { approver })
}

export function getPendingCount(applicant: string): Promise<R<number>> {
  return get(`${FLOW_BASE}/api/workflow/pending-count`, { applicant })
}

export function getWorkflowDefinitionDetail(id: number): Promise<R<DefinitionDetail>> {
  return get(`${FLOW_BASE}/api/workflow/definition/${id}`)
}

export function updateWorkflowDefinition(id: number, data: WorkflowCreateRequest): Promise<R<void>> {
  return put(`${FLOW_BASE}/api/workflow/definition/${id}`, data)
}

export function deleteWorkflowDefinition(id: number): Promise<R<void>> {
  return del(`${FLOW_BASE}/api/workflow/definition/${id}`)
}

export function toggleDefinitionStatus(id: number, enabled: boolean): Promise<R<void>> {
  return put(`${FLOW_BASE}/api/workflow/definition/${id}/status`, null, { params: { enabled } })
}

// ==================== 表单定义（表单与流程绑定） ====================

export function getWorkflowForms(params?: {
  formKey?: string
  formName?: string
  definitionKey?: string
  applyType?: string
  status?: number
}): Promise<R<WorkflowForm[]>> {
  return get(`${FLOW_BASE}/api/workflow/forms`, params)
}

export function getPublicFormOptions(): Promise<R<WorkflowForm[]>> {
  return get(`${FLOW_BASE}/api/workflow/forms/public/options`)
}

export function getWorkflowForm(formKey: string): Promise<R<WorkflowForm>> {
  return get(`${FLOW_BASE}/api/workflow/forms/${formKey}`)
}

export function createWorkflowForm(data: WorkflowForm): Promise<R<WorkflowForm>> {
  return post(`${FLOW_BASE}/api/workflow/forms`, data)
}

export function updateWorkflowForm(formKey: string, data: WorkflowForm): Promise<R<WorkflowForm>> {
  return put(`${FLOW_BASE}/api/workflow/forms/${formKey}`, data)
}

export function updateWorkflowFormStatus(formKey: string, status: number): Promise<R<boolean>> {
  return put(`${FLOW_BASE}/api/workflow/forms/${formKey}/status`, { status } as WorkflowForm)
}

export function deleteWorkflowForm(formKey: string): Promise<R<boolean>> {
  return del(`${FLOW_BASE}/api/workflow/forms/${formKey}`)
}

export function submitWorkflowByForm(data: {
  formKey: string
  title: string
  applyContent?: string
  applicant?: string
}): Promise<R<WorkflowInstance>> {
  return post(`${FLOW_BASE}/api/workflow/submit-by-form`, data)
}

