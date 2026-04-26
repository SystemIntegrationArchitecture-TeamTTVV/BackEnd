# TTVV Social Network — Backend

## System Architecture

The backend follows a **microservices architecture** consisting of four independently deployable services, coordinated through an API Gateway and communicating via both synchronous REST calls and asynchronous event-driven messaging.

```
                         Client Applications
                                |
                         API Gateway :8088
                     (JWT validation, routing,
                      circuit breaking, CORS)
                       /        |        \
                      /         |         \
             AuthService   CommonService   MessageService
               :8083          :8081           :8082
            (PostgreSQL)    (MongoDB)       (MongoDB)
                      \         |         /
                       \        |        /
                    Kafka (async events) :9092
                    Redis (token store)  :6379
```

### Service Responsibilities

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| **ApiGateway** | 8088 | — | Request routing, JWT validation, rate limiting (Resilience4j) |
| **AuthService** | 8083 | PostgreSQL (Supabase) | Authentication, user management, JWT issuance, password reset |
| **CommonService** | 8081 | MongoDB (Atlas) | Posts, comments, friends, groups, stories, notifications, AI moderation |
| **MessageService** | 8082 | MongoDB (Atlas) | Conversations, real-time messaging, calls, file sharing |

### Inter-Service Communication

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Synchronous | OpenFeign (REST) | CommonService to AuthService (user lookup), CommonService to MessageService |
| Asynchronous | Apache Kafka | Event-driven: `ttvv.user.registered`, notification dispatch |

### Security Model

Authentication is handled centrally at the API Gateway level:

1. **Gateway** validates incoming JWT tokens and injects trusted headers (`X-User-Id`, `X-Username`, `X-Role`) into downstream requests.
2. **Downstream services** read Gateway-injected headers directly — no redundant token parsing. A JWT fallback is available for direct access (Swagger UI, inter-service calls).
3. **Refresh tokens** are stored in Redis with configurable TTL. The frontend automatically refreshes expired access tokens and retries failed requests.

### Infrastructure Dependencies

| Component | Purpose | Managed By |
|-----------|---------|------------|
| Apache Kafka + Zookeeper | Asynchronous event streaming | `ops/docker-compose.infra.yml` |
| Redis | Token store, session caching | `ops/docker-compose.infra.yml` |
| Prometheus + Grafana | Metrics collection, dashboards | `ops/docker-compose.infra.yml` |

---

## Service Discovery

Service discovery is implemented using **Docker DNS resolution** — no Eureka or Consul required. Within the Docker network, services resolve each other by container name:

```
http://auth-service:8083
http://common-service:8081
http://message-service:8082
```

For local development, services default to `localhost` with standard ports.

## Configuration Management

Configuration is externalized using **Spring profiles** and **environment variables** — no Spring Cloud Config Server required.

| Profile | Activation | Service URLs |
|---------|-----------|-------------|
| `default` | Local development (`mvn spring-boot:run`) | `localhost:808x` |
| `docker` | Container runtime (set in Dockerfile) | Docker DNS names |

Configuration files per service:
- `application.properties` — base configuration with `localhost` defaults
- `application-docker.properties` — Docker DNS overrides (activated by `SPRING_PROFILES_ACTIVE=docker`)

All sensitive values support environment variable override using the `${ENV_VAR:default}` pattern.

---

## Deployment

### Docker Mode (Full Stack)

Prerequisites: Docker and Docker Compose installed.

```bash
# From project root (KIenTruc/)
docker compose up -d --build
```

This starts both the infrastructure layer (Kafka, Redis, Prometheus, Grafana) and all four application services on a shared Docker network.

Verify deployment:
```bash
curl http://localhost:8088/actuator/health
```

Teardown:
```bash
docker compose down
```

### Local Development Mode

Each service runs independently using default Spring profile with `localhost` addresses.

```bash
# Terminal 1
cd AuthService && mvn spring-boot:run

# Terminal 2
cd CommonService && mvn spring-boot:run

# Terminal 3
cd MessegeService && mvn spring-boot:run

# Terminal 4
cd ApiGateway && mvn spring-boot:run
```

Alternatively, use the provided startup script:
```powershell
.\start-be.ps1
```

If script execution is blocked:
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### Independent Service Deployment

Each service ships with its own `Dockerfile` and can be built, pushed, and deployed independently to any container platform (Railway, Render, Kubernetes, etc.):

```bash
docker build -t ttvv/auth-service ./AuthService
docker build -t ttvv/common-service ./CommonService
docker build -t ttvv/message-service ./MessegeService
docker build -t ttvv/api-gateway ./ApiGateway
```

Service URLs are configured via environment variables, enabling each service to point to any deployment target.

---

## Environment Variables Reference

| Variable | Services | Description |
|----------|----------|-------------|
| `JWT_SECRET` | All | HMAC-SHA signing key for JWT tokens |
| `APP_SECURITY_ENABLED` | All | Toggle JWT authentication (`true` / `false`) |
| `DB_URL` | AuthService | PostgreSQL JDBC connection string |
| `DB_USER` | AuthService | PostgreSQL username |
| `DB_PASSWORD` | AuthService | PostgreSQL password |
| `MONGODB_URI` | CommonService, MessageService | MongoDB Atlas connection string |
| `REDIS_HOST` | AuthService, CommonService | Redis server hostname |
| `REDIS_PASSWORD` | AuthService, CommonService | Redis authentication password |
| `KAFKA_BOOTSTRAP_SERVERS` | All | Kafka broker address |
| `APP_KAFKA_ENABLED` | All | Enable/disable Kafka integration |
| `GEMINI_API_KEY` | CommonService | Google Gemini API key for AI features |

Create a `BE/.env` file (git-ignored) to manage these values locally. Docker Compose reads `.env` automatically from the working directory.

---

## API Documentation

Swagger UI is available through the API Gateway at:

```
http://localhost:8088/swagger-ui.html
```

Individual service documentation:
- Auth Service: `http://localhost:8088/api/auth-svc/v3/api-docs`
- Common Service: `http://localhost:8088/api/common/v3/api-docs`
- Message Service: `http://localhost:8088/api/message/v3/api-docs`

---

## Monitoring

| Dashboard | URL | Credentials |
|-----------|-----|-------------|
| Grafana | `http://localhost:3000` | Configured in `ops/.env` |
| Prometheus | `http://localhost:9090` | — |
| Service Health | `http://localhost:8088/actuator/health` | — |
