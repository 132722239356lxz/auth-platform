$body = '{"username":"admin","password":"admin123","clientId":"admin-web"}'
Write-Host "Body: $body"
try {
    $r = Invoke-RestMethod -Uri 'http://127.0.0.1:9000/api/auth/login' -Method POST -ContentType 'application/json' -Body $body
    Write-Host "Response:"
    Write-Host ($r | ConvertTo-Json -Depth 10)
} catch {
    Write-Host "HTTP Error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $respBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "Status: $([int]$_.Exception.Response.StatusCode)"
        Write-Host "Response Body: $respBody"
    }
}
