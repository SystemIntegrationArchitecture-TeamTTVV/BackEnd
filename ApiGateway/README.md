# API Gateway - TTVV Social Network

API Gateway service để route requests đến các microservices.

## Ports

- **API Gateway**: `8080`
- **CommonService**: `8081`
- **MessegeService**: `8082`

## Routes

### CommonService Routes
- `/api/common/**` → `http://localhost:8081/**`

Ví dụ:
- `GET http://localhost:8080/api/common/users` → `GET http://localhost:8081/users`
- `POST http://localhost:8080/api/common/posts` → `POST http://localhost:8081/posts`

### MessegeService Routes
- `/api/message/**` → `http://localhost:8082/**`

Ví dụ:
- `GET http://localhost:8080/api/message/conversations` → `GET http://localhost:8082/conversations`
- `POST http://localhost:8080/api/message/messages` → `POST http://localhost:8082/messages`

## CORS

Gateway đã cấu hình CORS để cho phép tất cả origins, methods và headers.

## Actuator Endpoints

- Health: `http://localhost:8080/actuator/health`
- Gateway Routes: `http://localhost:8080/actuator/gateway/routes`

## Chạy Services

1. **Start MongoDB** (nếu chưa chạy)
2. **Start CommonService**: `cd CommonService && ./mvnw spring-boot:run`
3. **Start MessegeService**: `cd MessegeService && ./mvnw spring-boot:run`
4. **Start ApiGateway**: `cd ApiGateway && ./mvnw spring-boot:run`

## Testing

Test API Gateway:
```bash
# Test CommonService qua Gateway
curl http://localhost:8080/api/common/users

# Test MessegeService qua Gateway
curl http://localhost:8080/api/message/conversations
```

