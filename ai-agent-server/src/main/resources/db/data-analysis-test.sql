-- ============================================================
-- 数据分析预警测试数据
-- 数据库: auth_platform
-- 运行方式: 在 MySQL 客户端执行本脚本，或项目启动时自动加载
-- 说明:
--   1. 直接插入 ai_analysis_alert 表，让数据预警页面展示预警列表和汇总
--   2. 补充 msg_record 失败记录，让关键业务指标区域展示消息失败率等指标
--   3. wf_instance / wf_task 的测试数据已由 schema-workflow.sql 提供
-- ============================================================

-- 清除旧测试数据（可选，避免重复插入时主键冲突）
-- DELETE FROM ai_analysis_alert WHERE id <= 10;
-- DELETE FROM msg_record WHERE id <= 10;

-- ============================================================
-- Part 1: 分析预警记录 (8 条，覆盖 CRITICAL/WARN/INFO 三种级别和多种分析类型)
-- ============================================================
INSERT INTO ai_analysis_alert (
    id, alert_name, analysis_type, data_source, alert_level, alert_content,
    analysis_detail, suggestion, metrics_json, is_read, resolved, resolved_at, resolved_by, created_at
) VALUES (
    1, '待审批任务严重积压', 'THRESHOLD', 'wf_task', 'CRITICAL',
    '当前待审批任务数达到 28，超过严重阈值 20，需立即处理。',
    '近 7 日审批任务流入量持续高于处理量，导致待审批队列积压。主要积压节点：部门负责人审批（lisi）、系统管理员审批（admin）。',
    '建议立即加派审批人手，或启动临时审批绿色通道；同时复盘审批 SLA 并设置超时提醒。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 09:30:00'
), (
    2, '消息发送失败率异常', 'RATIO', 'msg_record', 'CRITICAL',
    '近 1 天消息发送失败率为 4.2%，超过阈值 3%。',
    '失败主要集中在短信渠道（AliyunSmsProvider），失败原因多为模板变量缺失和接收号码格式错误。',
    '建议立即检查消息模板变量配置，补充接收号码格式校验；对短信渠道进行失败重试策略优化。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 10:15:00'
), (
    3, '工作流日处理量骤降', 'CHANGE_RATE', 'wf_instance', 'WARN',
    '今日工作流处理量较 7 日平均值下降 45%，需关注业务活跃度。',
    '今日已处理审批数 7，近 7 天日均 8.5。下降原因可能是临近周末或系统响应变慢导致用户提交减少。',
    '建议检查系统响应时间，排查是否存在性能瓶颈；同时主动推送审批待办提醒，提升处理量。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 11:00:00'
), (
    4, '工作流驳回率偏高', 'RATIO', 'wf_instance', 'WARN',
    '近 7 天工作流驳回率为 6.7%，高于正常阈值 5%。',
    '驳回原因集中在申请内容不完整、权限范围过大。建议优化申请表单提示和权限申请模板。',
    '建议优化申请表单必填项提示，增加权限申请最小化原则说明；对高频驳回场景进行审批人培训。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 11:30:00'
), (
    5, '未处理预警积压', 'THRESHOLD', 'ai_analysis_alert', 'WARN',
    '当前未处理预警数为 8，超过阈值 5，建议及时清理。',
    '未处理预警中包含 3 条 CRITICAL 级别，需优先处理；其余为 WARN 和 INFO 级别。',
    '建议建立预警分级处理机制，CRITICAL 级别 30 分钟内响应，WARN 级别 2 小时内响应。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 12:00:00'
), (
    6, '系统登录异常趋势', 'TREND', 'sys_log', 'INFO',
    '近 3 天登录失败次数呈上升趋势，但尚未达到预警阈值。',
    '登录失败次数从每日 5 次上升至 12 次，主要来源为密码错误和验证码过期。',
    '建议持续观察，若单日失败次数超过 20 则升级为 WARN；同时优化验证码有效期和登录提示。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 13:00:00'
), (
    7, 'API 响应时延预测', 'PREDICTION', 'gateway_log', 'INFO',
    '基于近 7 天趋势预测，下周平均 API 响应时延可能增长 15%。',
    '随着业务量增长，部分高耗时接口（如知识库搜索、日志查询）的 P95 时延呈上升趋势。',
    '建议提前对高耗时接口进行性能优化，考虑增加缓存或异步处理。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 14:00:00'
), (
    8, '定时自动分析 - 综合洞察', 'INSIGHT', 'multi_source', 'INFO',
    '业务运行整体正常，综合洞察报告已生成。',
    '所有关键指标处于正常区间，未发现明显异常。建议持续监控待审批任务数和消息发送成功率。',
    '建议保持当前监控策略，定期复核预警阈值是否合理。',
    '{"pendingTaskCount":28,"todayCreatedCount":12,"rejectCount":3,"totalApprovalCount":45,"avgLastWeekCount":8.5,"todayCount":7,"failCount":5,"totalSendCount":120,"unresolvedAlertCount":8,"criticalAlertCount":3}',
    0, 0, NULL, NULL, '2026-07-17 15:00:00'
);

-- ============================================================
-- Part 2: 消息发送失败记录（用于关键业务指标展示）
-- ============================================================
INSERT INTO msg_record (
    id, message_id, message_type, title, content, channels, status,
    source_system, sender, receivers, business_id, fail_reason, retry_count, create_time, send_time
) VALUES (
    1, 'MSG-20260717-0001', 'SYSTEM_NOTICE', '系统维护通知', '今晚 22:00 进行系统维护。',
    'SMS,EMAIL', 'FAILED', 'auth-server', 'system', '13800138000', 'MAINT-001',
    '短信模板变量缺失', 2, '2026-07-17 09:00:00', '2026-07-17 09:00:00'
), (
    2, 'MSG-20260717-0002', 'APPROVAL_NOTIFY', '审批结果通知', '您的权限申请已审批通过。',
    'IN_APP', 'SENT', 'auth-flow', 'flow-service', 'zhangsan', 'WF-1001',
    NULL, 0, '2026-07-17 09:05:00', '2026-07-17 09:05:00'
), (
    3, 'MSG-20260717-0003', 'EVENT_PUSH', '子系统事件通知', '子系统 A 上报健康检查异常。',
    'WEBSOCKET,MQ', 'FAILED', 'auth-message', 'subsystem-a', 'admin', 'EVT-001',
    'WebSocket 连接断开', 1, '2026-07-17 09:10:00', '2026-07-17 09:10:00'
), (
    4, 'MSG-20260717-0004', 'SYSTEM_NOTICE', '安全策略更新', '请尽快更新您的登录密码。',
    'EMAIL,IN_APP', 'SENT', 'auth-server', 'system', 'admin', 'SEC-001',
    NULL, 0, '2026-07-17 09:15:00', '2026-07-17 09:15:00'
), (
    5, 'MSG-20260717-0005', 'USER_MESSAGE', '验证码短信', '您的验证码是 123456，5 分钟内有效。',
    'SMS', 'FAILED', 'auth-server', 'system', '13800138001', 'OTP-001',
    '接收号码格式错误', 0, '2026-07-17 09:20:00', '2026-07-17 09:20:00'
);
