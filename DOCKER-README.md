# 🐳 TTVV Microservices — Docker & Local Run Guide

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                docker compose up -d                      │
│  ┌─────────────────────┐  ┌────────────────────────────┐ │
│  │ ops/infra.yml       │  │ BE/services.yml            │ │
│  │  Kafka    :9092     │  │  api-gateway    :8088      │ │
│  │  Redis    :6379     │  │  auth-service   :8083      │ │
│  │  Prometheus :9090   │  │  common-service :8081      │ │
│  │  Grafana  :3000     │  │  message-service:8082      │ │
│  └─────────────────────┘  └────────────────────────────┘ │
│               network: ttvv-infra_infra                  │
└──────────────────────────────────────────────────────────┘
```

**Service Discovery**: Docker DNS — services resolve each other by container name:
- `http://auth-service:8083`
- `http://common-service:8081`
- `http://message-service:8082`

**Configuration Management**: Spring profiles + environment variables (no Config Server).

---

## ▶️ Mode 1: Docker (toàn bộ)

```bash
# Từ thư mục root (KIenTruc/)
docker compose up -d --build

# Kiểm tra health
curl http://localhost:8088/actuator/health
```

Lệnh trên sẽ start:
1. **Infra**: Kafka, Redis, Prometheus, Grafana
2. **Services**: Gateway, Auth, Common, Message

Services tự động dùng profile `docker` → resolve DNS qua container names.

### Dừng
```bash
docker compose down
```

---

## ▶️ Mode 2: Local (dev từng service)

Mỗi service chạy trên máy local với Spring profile `default` → dùng `localhost`.

```bash
# Terminal 1: AuthService (port 8083)
cd BE/AuthService
mvn spring-boot:run

# Terminal 2: CommonService (port 8081)
cd BE/CommonService
mvn spring-boot:run

# Terminal 3: MessageService (port 8082)
cd BE/MessegeService
mvn spring-boot:run

# Terminal 4: Gateway (port 8088)
cd BE/ApiGateway
mvn spring-boot:run
```

Hoặc dùng script sẵn:
```bash
cd BE
.\start-be.ps1
```

### Infra local (tuỳ chọn)
Nếu cần Kafka/Redis local:
```bash
docker compose -f ops/docker-compose.infra.yml up -d
```

---

## ⚙️ Spring Profiles

| Profile | Khi nào | Service URLs |
|---------|---------|-------------|
| `default` | `mvn spring-boot:run` | `localhost:808x` |
| `docker` | Docker container | `http://auth-service:8083`, etc. |

Profile `docker` được activate tự động trong Dockerfile qua `SPRING_PROFILES_ACTIVE=docker`.

File config:
- `application.properties` — defaults (localhost)
- `application-docker.properties` — Docker DNS overrides

---

## 🔐 Environment Variables

Tất cả credentials nên override qua env vars trong production:

| Variable | Service | Description |
|----------|---------|-------------|
| `JWT_SECRET` | All | JWT signing key |
| `DB_URL` | AuthService | PostgreSQL connection URL |
| `DB_USER` | AuthService | PostgreSQL username |
| `DB_PASSWORD` | AuthService | PostgreSQL password |
| `MONGODB_URI` | Common, Message | MongoDB Atlas connection string |
| `REDIS_HOST` | Auth, Common | Redis hostname |
| `REDIS_PASSWORD` | Auth, Common | Redis password |
| `KAFKA_BOOTSTRAP_SERVERS` | All | Kafka broker address |
| `GEMINI_API_KEY` | Common | Google Gemini API key |
| `APP_SECURITY_ENABLED` | All | `true`/`false` — toggle JWT auth |

### Sử dụng .env file
Tạo `BE/.env` (đã .gitignore):
```env
JWT_SECRET=my-production-secret-key-here
DB_PASSWORD=your-supabase-password
MONGODB_URI=mongodb+srv://user:pass@cluster/db
```

Docker Compose tự đọc `.env` cùng thư mục.
