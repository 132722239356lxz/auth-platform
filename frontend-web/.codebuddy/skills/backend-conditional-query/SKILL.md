---
name: backend-conditional-query
description: >
  所有页面必须通过后端接口进行条件查询，禁止使用前端侧筛选（如 computed 过滤、Array.filter() 等）。
  This skill should be used when modifying or creating any query/list page in the message center module (收件箱/inbox, 消息模板/template, 子系统事件/events, 异常反馈/incident).
---

# 消息中心后端条件查询规范

## 核心原则

**所有列表页的筛选、搜索、排序、分页操作，必须将条件作为查询参数传递给后端 API，由后端执行过滤后返回结果。前端仅负责展示，不得对后端返回的数据做二次筛选。**

## 为什么禁止前端筛选

1. **数据量问题**：前端一次加载全部数据后做筛选，当数据量大时会造成严重的性能问题和内存占用。
2. **分页错乱**：前端筛选后再分页会导致分页结果不正确，总数与实际不符。
3. **一致性**：筛选逻辑应由后端统一维护，避免前后端逻辑不一致。
4. **网络开销**：前端筛选需要拉取全量数据，浪费带宽。

## 标准实现模式

### 正确做法 ✅

```typescript
// 1. 定义筛选表单响应式数据
const filter = reactive({
  keyword: '',
  messageType: '',
  status: '',
  startTime: '',
  endTime: '',
  page: 1,
  size: 20
})

// 2. 加载数据时，将筛选条件组装为后端 API 的查询参数
async function loadData() {
  loading.value = true
  try {
    const res = await queryMessages({
      keyword: filter.keyword || undefined,
      messageType: filter.messageType || undefined,
      status: filter.status || undefined,
      startTime: filter.startTime || undefined,
      endTime: filter.endTime || undefined,
      page: filter.page,
      size: filter.size
    })
    // 3. 直接将后端返回的数据赋值，不做任何前端过滤
    const data = res?.data
    messages.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    messages.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 4. 查询/重置时重置页码并从后端重新加载
function handleSearch() {
  filter.page = 1
  loadData()
}

function handleReset() {
  filter.keyword = ''
  filter.messageType = ''
  filter.status = ''
  filter.page = 1
  loadData()
}
```

### 错误做法 ❌

```typescript
// ❌ 从后端拉取全量数据后前端侧过滤
const allMessages = ref<MessageInfo[]>([])
const filteredMessages = computed(() =>
  allMessages.value.filter(m =>
    (!filter.keyword || m.title?.includes(filter.keyword)) &&
    (!filter.messageType || m.messageType === filter.messageType)
  )
)

// ❌ 手动前端分页
const pagedMessages = computed(() =>
  filteredMessages.value.slice((page - 1) * size, page * size)
)
```

## 消息中心各页面后端 API 对照

| 页面 | API 函数 | 端点 | 支持的查询参数 |
|------|---------|------|--------------|
| `inbox.vue` (收件箱) | `queryMessages()` | `GET /auth-message/api/message/list` | keyword, messageType, status, channels, receiver, startTime, endTime, page, size |
| `template.vue` (模板) | `getMessageTemplates()` | `GET /auth-message/api/message/template/list` | keyword, templateCode, channel, status, startTime, endTime, page, size |
| `events.vue` (事件) | `queryMessages()` | `GET /auth-message/api/message/list` | keyword, messageType, status, channels, receiver, startTime, endTime, page, size |
| `incident.vue` (异常) | `queryIncidents()` | `GET /auth-message/api/subsystem/incident/list` | subsystem, level, status, page, size |

## 实现检查清单

当新增或修改消息中心列表页时，确认以下各项：

- [ ] 页面的所有筛选条件都有对应的后端 API 查询参数
- [ ] `loadData()` 函数将筛选值拼入 API 请求参数（空值传 `undefined`）
- [ ] 后端返回的数据直接赋值给列表响应式变量，无 `.filter()` / `computed` 二次过滤
- [ ] 分页的 `page`、`size` 参数传递给后端
- [ ] `total` 从后端返回的 `res.data.total` 获取，不由前端计算
- [ ] 查询按钮触发 `filter.page = 1` + `loadData()`
- [ ] 重置按钮清空筛选字段后调用 `loadData()`
- [ ] 筛选条件变化时自动触发后端重新查询（`@change="loadData"` / `@clear="loadData"`）
