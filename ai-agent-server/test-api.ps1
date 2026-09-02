# ============================================
# AI智能体服务 - API测试脚本 (PowerShell)
# 使用方式: .\test-api.ps1
# 前置: ai-agent-server 启动在 9003 端口
# ============================================

$Base = "http://localhost:9003"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  AI智能体服务 API 测试" -ForegroundColor Cyan
Write-Host "  Base URL: $Base" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$headers = @{ "Content-Type" = "application/json" }

function Invoke-Api {
    param($Label, $Method, $Path, $Body)
    Write-Host "`n--- $Label ---" -ForegroundColor Yellow
    $params = @{ Uri = "$Base$Path"; Method = $Method; Headers = $headers }
    if ($Body) { $params.Body = $Body }
    $response = Invoke-RestMethod @params -ErrorAction SilentlyContinue
    if ($response) { $response | ConvertTo-Json -Depth 3 }
}

Invoke-Api "1. 健康检查" GET "/api/ai/health/status"
Invoke-Api "2. 添加知识库文档" POST "/api/ai/knowledge/doc" `
  '{"kbName":"test-kb","title":"使用手册","content":"AI智能体使用说明","contentType":"TEXT"}'
Invoke-Api "3. 知识库列表" GET "/api/ai/knowledge/kb"
Invoke-Api "4. 本地搜索" GET "/api/ai/search/local?q=AI&n=5"
Invoke-Api "5. 业务指标" GET "/api/ai/analysis/metrics"
Invoke-Api "6. 触发预警" POST "/api/ai/analysis/trigger"
Invoke-Api "7. 预警汇总" GET "/api/ai/analysis/alerts/summary"
Invoke-Api "8. AI快速问答" GET "/api/ai/chat/quick?q=查看待审批任务数"
Invoke-Api "9. AI对话" POST "/api/ai/chat/ask" `
  '{"sessionId":"ps-test","question":"获取当前业务指标","useMemory":true}'
Invoke-Api "10. 服务状态" GET "/api/ai/chat/status"
Invoke-Api "11. 清除会话" DELETE "/api/ai/chat/session/ps-test"
Invoke-Api "12. 清理测试数据" DELETE "/api/ai/knowledge/kb/test-kb"

Write-Host "`n==========================================" -ForegroundColor Green
Write-Host "  测试完成" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
