# Backend TTVV (Spring Boot)

## Chạy nhanh cả stack (Eureka + Common + Message + Gateway)

Từ thư mục `BE`:

```powershell
.\start-be.ps1
```

Hoặc double-click **`start-be.bat`**.

- Mở **4 cửa sổ** PowerShell (mỗi service một log).
- Dùng **Maven Wrapper** trong `CommonService` (không cần cài Maven global).
- Thứ tự: Eureka `8761` → Common `8081` → Message `8082` → Gateway `8080`.

Nếu bị chặn script:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

**Gateway:** http://localhost:8080 — health: http://localhost:8080/actuator/health

## Chạy từng service thủ công

Xem `ApiGateway/README.md`.
