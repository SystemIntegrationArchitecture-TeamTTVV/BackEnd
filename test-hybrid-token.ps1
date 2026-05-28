$GATEWAY = "http://localhost:8088"
$AUTH_SERVICE = "http://localhost:8083"

function OK($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function FAIL($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }
function INFO($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function STEP($msg) { Write-Host "`n===== $msg =====" -ForegroundColor Yellow }

function Decode-JwtPayload($token) {
    $parts = $token.Split('.')
    if ($parts.Length -lt 2) { return $null }
    $payload = $parts[1]
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    $payload = $payload.Replace('-', '+').Replace('_', '/')
    $json = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
    return $json | ConvertFrom-Json
}

STEP "TEST 0: Health Check"

try {
    $health = Invoke-RestMethod -Uri "$GATEWAY/actuator/health" -Method GET -TimeoutSec 5
    OK "Gateway is UP: $($health.status)"
} catch {
    FAIL "Gateway is DOWN at $GATEWAY"
    exit 1
}

try {
    $health = Invoke-RestMethod -Uri "$AUTH_SERVICE/actuator/health" -Method GET -TimeoutSec 5
    OK "AuthService is UP: $($health.status)"
} catch {
    FAIL "AuthService is DOWN at $AUTH_SERVICE"
    exit 1
}

STEP "TEST 1: Login"

$captchaToken = ""
$captchaText = ""

try {
    $captcha = Invoke-RestMethod -Uri "$GATEWAY/api/auth/captcha/challenge" -Method GET -TimeoutSec 10
    $captchaToken = $captcha.token

    $base64 = $captcha.image -replace '^data:image/png;base64,', ''
    [IO.File]::WriteAllBytes("captcha.png", [Convert]::FromBase64String($base64))

    Start-Process "captcha.png"

    $captchaText = Read-Host "Nhap ma captcha trong anh captcha.png"
    INFO "Captcha received"
} catch {
    INFO "Captcha endpoint failed, login without captcha"
}

$loginBody = @{
    username = "lannguyen"
    password = "password123"
    captchaToken = $captchaToken
    captchaText = $captchaText
} | ConvertTo-Json

try {
    try {
        $loginResponse = Invoke-RestMethod -Uri "$GATEWAY/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody -TimeoutSec 10
    } catch {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host $body
        exit 1
    }

    $accessToken = if ($loginResponse.token) { $loginResponse.token } else { $loginResponse.accessToken }
    $refreshToken = $loginResponse.refreshToken
    $userId = $loginResponse.userId

    if (-not $accessToken) {
        FAIL "Login failed: no access token"
        exit 1
    }

    OK "Login success"
    INFO "Username: $($loginResponse.username)"
    INFO "UserId: $userId"

    $payload = Decode-JwtPayload $accessToken

    if ($payload.jti) {
        OK "Access token has JTI: $($payload.jti)"
    } else {
        FAIL "Access token does NOT have JTI"
    }
} catch {
    FAIL "Login failed: $($_.Exception.Message)"
    exit 1
}

STEP "TEST 2: Protected API before logout"
INFO "Note: /api/users/ is PUBLIC. Using /api/common/notifications (protected) instead"

$headers = @{
    Authorization = "Bearer $accessToken"
    "Content-Type" = "application/json"
}

$PROTECTED_URL = "$GATEWAY/api/common/notifications"

try {
    $response = Invoke-WebRequest -Uri $PROTECTED_URL -Method GET -Headers $headers -TimeoutSec 10 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        OK "Protected API returned 200"
    } else {
        FAIL "Unexpected status: $($response.StatusCode)"
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        FAIL "Protected API returned 401 before logout - JWT verification issue"
    } else {
        INFO "Protected API returned status $statusCode (may be expected if no data)"
        OK "Token was accepted by Gateway (not 401)"
    }
}

STEP "TEST 3: Logout and blacklist"

try {
    $logoutBody = @{ refreshToken = $refreshToken } | ConvertTo-Json
    $logoutResponse = Invoke-RestMethod -Uri "$GATEWAY/api/auth/logout" -Method POST -ContentType "application/json" -Headers @{ Authorization = "Bearer $accessToken" } -Body $logoutBody -TimeoutSec 10
    OK "Logout success"
} catch {
    FAIL "Logout failed: $($_.Exception.Message)"
}

Start-Sleep -Seconds 1

try {
    $response = Invoke-WebRequest -Uri $PROTECTED_URL -Method GET -Headers $headers -TimeoutSec 10 -UseBasicParsing
    FAIL "Old access token still works (status $($response.StatusCode)). Blacklist NOT working!"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        OK "Old access token rejected with 401. Blacklist works!"
    } else {
        INFO "Old access token rejected with status: $statusCode"
    }
}

STEP "TEST 4: Refresh token after logout"

try {
    $refreshBody = @{ refreshToken = $refreshToken } | ConvertTo-Json
    $refreshResponse = Invoke-RestMethod -Uri "$GATEWAY/api/auth/refresh" -Method POST -ContentType "application/json" -Body $refreshBody -TimeoutSec 10

    if ($refreshResponse.token -or $refreshResponse.accessToken) {
        FAIL "Old refresh token still works"
    } else {
        INFO "Refresh response has no token"
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        OK "Old refresh token rejected with 401"
    } else {
        OK "Old refresh token rejected with status: $statusCode"
    }
}

STEP "DONE"