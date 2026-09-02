# OAuth2 End-to-End Test Script
# Flow: login -> init-session -> authorize -> exchange-code
$ErrorActionPreference = "Stop"

$AUTH_SERVER = "http://127.0.0.1:9000"
$CLIENT_ID = "admin-web"
$REDIRECT_URI = "http://127.0.0.1:5173/login/callback"
$USERNAME = "admin"
$PASSWORD = "admin123"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " OAuth2 End-to-End Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Password login -> Bearer Token
Write-Host "[Step 1] Password login -> Bearer Token..." -ForegroundColor Yellow

$loginBody = @{ username = $USERNAME; password = $PASSWORD; clientId = $CLIENT_ID } | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$AUTH_SERVER/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
    $accessToken = $loginResponse.data.access_token
    Write-Host "  [OK] Login success" -ForegroundColor Green
    Write-Host "  access_token prefix: $($accessToken.Substring(0, [Math]::Min(30, $accessToken.Length)))..." -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] Login failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "  Response: $($reader.ReadToEnd())" -ForegroundColor Red
        $reader.Close()
    }
    exit 1
}

# Step 2: init-session -> Session Cookie
Write-Host "[Step 2] init-session -> Session Cookie..." -ForegroundColor Yellow

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

try {
    $headers = @{ Authorization = "Bearer $accessToken" }
    $initResponse = Invoke-RestMethod -Uri "$AUTH_SERVER/api/auth/init-session" -Method POST -ContentType "application/json" -Headers $headers -WebSession $session
    Write-Host "  [OK] init-session success" -ForegroundColor Green
    Write-Host "  SessionId: $($initResponse.data.sessionId)" -ForegroundColor Gray
    Write-Host "  Principal: $($initResponse.data.principal)" -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] init-session failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "  Response: $($reader.ReadToEnd())" -ForegroundColor Red
        $reader.Close()
    }
    exit 1
}

# Step 3: authorize -> Authorization Code
Write-Host "[Step 3] /oauth2/authorize -> Get Code..." -ForegroundColor Yellow

# state = 子系统的回调地址
$state = $REDIRECT_URI
$encodedRedirect = [uri]::EscapeDataString($REDIRECT_URI)
$authorizeUrl = "${AUTH_SERVER}/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${encodedRedirect}&scope=openid+profile&state=${encodedRedirect}"
Write-Host "  URL: $authorizeUrl" -ForegroundColor Gray

try {
    $null = Invoke-WebRequest -Uri $authorizeUrl -Method GET -WebSession $session -MaximumRedirection 0 -ErrorAction Stop
} catch [System.Net.WebException] {
    # MaximumRedirection 0 throws WebException for 302
    $response = $_.Exception.Response
    $statusCode = [int]$response.StatusCode
    Write-Host "  Response Status: $statusCode" -ForegroundColor Gray

    if ($statusCode -eq 302) {
        $location = $response.Headers['Location']
        Write-Host "  Redirect: $location" -ForegroundColor Gray

        $uriBuilder = [System.UriBuilder]$location
        $query = [System.Web.HttpUtility]::ParseQueryString($uriBuilder.Query)
        $code = $query['code']
        $returnedState = $query['state']

        if ($code) {
            Write-Host "  [OK] Got authorization code: $code" -ForegroundColor Green
            Write-Host "  State match: $($state -eq $returnedState)" -ForegroundColor Gray
        } else {
            Write-Host "  [FAIL] No code in redirect URL" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "  [FAIL] Expected 302, got $statusCode" -ForegroundColor Red
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        Write-Host "  Response: $($reader.ReadToEnd())" -ForegroundColor Red
        $reader.Close()
        exit 1
    }
} catch {
    Write-Host "  [FAIL] Authorize error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 4: exchange-code -> Token (server-side, client_secret safe)
Write-Host "[Step 4] exchange-code -> Get Token..." -ForegroundColor Yellow

$exchangeBody = @{ code = $code; state = $state; redirectUri = $REDIRECT_URI } | ConvertTo-Json

try {
    $headers = @{ Authorization = "Bearer $accessToken" }
    $exchangeResponse = Invoke-RestMethod -Uri "$AUTH_SERVER/api/auth/exchange-code" -Method POST -ContentType "application/json" -Headers $headers -Body $exchangeBody

    if ($exchangeResponse.code -eq 200) {
        $td = $exchangeResponse.data
        Write-Host "  [OK] Token exchange success!" -ForegroundColor Green
        Write-Host "  access_token: $($td.access_token.Substring(0, [Math]::Min(40, $td.access_token.Length)))..." -ForegroundColor Green
        Write-Host "  refresh_token: $($td.refresh_token.Substring(0, [Math]::Min(40, $td.refresh_token.Length)))..." -ForegroundColor Green
        Write-Host "  token_type: $($td.token_type)" -ForegroundColor Gray
        Write-Host "  scope: $($td.scope)" -ForegroundColor Gray
        if ($td.expires_in) { Write-Host "  expires_in: $($td.expires_in)" -ForegroundColor Gray }
    } else {
        Write-Host "  [FAIL] code=$($exchangeResponse.code), message=$($exchangeResponse.message)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  [FAIL] Token exchange error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "  Response: $($reader.ReadToEnd())" -ForegroundColor Red
        $reader.Close()
    }
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " ALL TESTS PASSED!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Flow Summary:" -ForegroundColor White
Write-Host "  1. Password login -> Bearer Token" -ForegroundColor Gray
Write-Host "  2. init-session -> Session Cookie" -ForegroundColor Gray
Write-Host "  3. authorize (with cookie) -> Authorization Code" -ForegroundColor Gray
Write-Host "  4. exchange-code (server-side) -> Token" -ForegroundColor Gray
