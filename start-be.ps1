# Chạy toàn bộ backend TTVV: Eureka → Common → Message → Gateway
# Dùng Maven Wrapper của CommonService (không cần cài Maven global).
# Mỗi service một cửa sổ PowerShell riêng để xem log dễ.

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Mvnw = Join-Path $Root "CommonService\mvnw.cmd"
$CommonDir = Join-Path $Root "CommonService"

if (-not (Test-Path $Mvnw)) {
    Write-Host "[start-be] Không tìm thấy CommonService\mvnw.cmd" -ForegroundColor Red
    exit 1
}

# Thứ tự: Eureka trước, sau đó các service còn lại
$steps = @(
    @{ Name = "EurekaServer :8761";  Args = "-f `"$Root\EurekaServer\pom.xml`" spring-boot:run -DskipTests" }
    @{ Name = "CommonService :8081"; Args = "-f `"$Root\CommonService\pom.xml`" spring-boot:run -DskipTests" }
    @{ Name = "MessegeService :8082"; Args = "-f `"$Root\MessegeService\pom.xml`" spring-boot:run -DskipTests" }
    @{ Name = "ApiGateway :8080";    Args = "-f `"$Root\ApiGateway\pom.xml`" spring-boot:run -DskipTests" }
)

Write-Host ""
Write-Host "  TTVV Backend - mở $($steps.Count) cửa sổ..." -ForegroundColor Cyan
Write-Host "  Gateway: http://localhost:8080  |  Health: http://localhost:8080/actuator/health" -ForegroundColor DarkGray
Write-Host ""

$i = 0
foreach ($s in $steps) {
    $i++
    if ($i -gt 1) {
        # Cho Eureka kịp lên trước khi bật các service đăng ký
        $wait = if ($i -eq 2) { 8 } else { 3 }
        Start-Sleep -Seconds $wait
    }

    $title = "TTVV - $($s.Name)"
    $inner = "`$Host.UI.RawUI.WindowTitle = '$title'; & `"$Mvnw`" $($s.Args)"

    Start-Process powershell -WorkingDirectory $CommonDir -ArgumentList @(
        "-NoExit",
        "-Command",
        $inner
    )

    Write-Host "  [$i/$($steps.Count)] Đã bật: $($s.Name)" -ForegroundColor Green
}

Write-Host ""
Write-Host "  Xong. Đợi vài chục giây để các service compile + khởi động." -ForegroundColor Yellow
Write-Host ""
