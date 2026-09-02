export interface ChannelOption {
  label: string
  value: string
  type: '' | 'info' | 'success' | 'warning' | 'danger'
}

export const channelOptions: ChannelOption[] = [
  { label: '站内信', value: 'IN_APP', type: '' },
  { label: 'WebSocket', value: 'WEBSOCKET', type: 'info' },
  { label: '短信', value: 'SMS', type: 'warning' },
  { label: '邮件', value: 'EMAIL', type: '' },
  { label: 'MQ', value: 'MQ', type: 'danger' }
]

const channelMap = new Map(channelOptions.map(o => [o.value, o]))

export function channelLabel(value?: string): string {
  return channelMap.get(value || '')?.label || value || '-'
}

export function channelType(value?: string): ChannelOption['type'] {
  return channelMap.get(value || '')?.type || 'info'
}
