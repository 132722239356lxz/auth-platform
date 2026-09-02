# 直接测试 ai-agent-server 的 /api/ai/chat/stream（绕过网关/Vite代理）
$ErrorActionPreference = "Stop"

$AI_SERVER = "http://127.0.0.1:9003"
$TOKEN = Read-Host "请输入 Bearer Token（从浏览器 DevTools 复制）"

function Test-JsonStream {
    Write-Host "`n[TEST 1] JSON Content-Type (text-only chat)..." -ForegroundColor Cyan
    $headers = @{
        Authorization = "Bearer $TOKEN"
        "Content-Type" = "application/json"
    }
    $body = '{"question":"你好","useMemory":true,"thinking":true}'
    try {
        $resp = Invoke-WebRequest -Uri "$AI_SERVER/ai-agent-server/api/ai/chat/stream" -Method POST -Headers $headers -Body $body -TimeoutSec 10
        Write-Host "  Status: $($resp.StatusCode)" -ForegroundColor Green
        Write-Host "  Content-Type: $($resp.Headers['Content-Type'])" -ForegroundColor Green
        Write-Host "  Body preview: $($resp.Content.Substring(0, [Math]::Min(200, $resp.Content.Length)))" -ForegroundColor Gray
    } catch {
        Write-Host "  FAILED: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host "  Response: $($reader.ReadToEnd())" -ForegroundColor Red
            $reader.Close()
        }
    }
}

function Test-MultipartStream {
    Write-Host "`n[TEST 2] Multipart Content-Type (text-only via FormData)..." -ForegroundColor Cyan
    $boundary = [Guid]::NewGuid().ToString().Replace("-", "")
    $headers = @{
        Authorization = "Bearer $TOKEN"
        "Content-Type" = "multipart/form-data; boundary=$boundary"
    }
    $body = @"
--$boundary
Content-Disposition: form-data; name="question"

你好
--$boundary
Content-Disposition: form-data; name="useMemory"

true
--$boundary
Content-Disposition: form-data; name="thinking"

true
--$boundary--
"@
    try {
        $resp = Invoke-WebRequest -Uri "$AI_SERVER/ai-agent-server/api/ai/chat/stream" -Method POST -Headers $headers -Body $body -TimeoutSec 10
        Write-Host "  Status: $($resp.StatusCode)" -ForegroundColor Green
        Write-Host "  Content-Type: $($resp.Headers['Content-Type'])" -ForegroundColor Green
        Write-Host "  Body preview: $($resp.Content.Substring(0, [Math]::Min(200, $resp.Content.Length)))" -ForegroundColor Gray
    } catch {
        Write-Host "  FAILED: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host "  Response: $($reader.ReadToEnd())" -ForegroundColor Red
            $reader.Close()
        }
    }
}

Test-JsonStream
Test-MultipartStream
