# Structure Gateway

基于 Spring Cloud Gateway 的多租户 API 网关服务，提供统一的 API 入口、安全认证、租户隔离和限流等功能。

## 功能特性

### 核心功能

- **路由转发**：基于路径的请求路由到不同的微服务
- **服务发现**：集成 Nacos 服务注册与发现
- **配置中心**：支持 Nacos 配置中心动态刷新
- **CORS 跨域**：全局跨域支持

### 安全与认证

- **Token 验证**：验证 Bearer Token 格式和有效性
- **租户识别**：自动识别和处理租户信息
- **重放攻击防护**：时间戳 + Nonce 机制防止重放攻击

### 限流控制

- **QPS 限流**：按每秒请求数限制
- **日限流**：按每日请求总数限制
- **月限流**：按每月请求总数限制
- **白名单支持**：白名单租户不限流
- **动态限流配置**：通过 Redis 存储和读取租户套餐配置

### 链路追踪

- **请求 ID 生成和传递**：自动生成或传递 X-Request-Id

### 统一响应格式

- **全局异常处理**：所有错误响应使用统一的 JSON 格式
- **业务错误码**：规范的业务错误码定义

## 技术栈

- **Java 8**
- **Spring Boot 2.7.18**
- **Spring Cloud Gateway**
- **Spring Cloud Alibaba (Nacos)**
- **Spring Data Redis (Reactive)**
- **RabbitMQ**
- **Hutool 工具库**
- **Lombok**

## 项目结构

```
structure-gateway/
├── src/main/java/cn/structured/cloud/gateway/
│   ├── config/              # 配置类
│   │   ├── GlobalGatewayFilter.java
│   │   ├── RabbitMQConfig.java
│   │   ├── RedisConfig.java
│   │   ├── RouteLocatorConfig.java
│   │   ├── DynamicRouteConfig.java
│   │   ├── StructureGatewayProperties.java
│   │   └── ErrorHandlerConfig.java
│   ├── constant/            # 常量定义
│   │   └── GatewayConstants.java
│   ├── dto/                 # 数据传输对象
│   │   └── TenantPackageMessage.java
│   ├── exception/           # 异常处理
│   │   └── GlobalExceptionHandler.java
│   ├── filter/              # 网关过滤器
│   │   ├── ReplayAttackPreventionFilter.java  # 重放攻击防护
│   │   ├── TenantIdentificationFilter.java    # 租户识别
│   │   ├── TenantRateLimitFilter.java         # 租户限流
│   │   ├── TokenVerificationFilter.java       # Token验证
│   │   ├── TraceHeaderFilter.java             # 链路追踪
│   │   └── RequestLoggingFilter.java          # 请求日志
│   ├── listener/            # 消息监听器
│   │   └── TenantPackageMessageListener.java
│   └── GatewayApplication.java               # 启动类
├── src/main/resources/
│   ├── application.yaml     # 应用配置
│   └── bootstrap.yaml       # 引导配置
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- Redis 5.0+
- RabbitMQ 3.8+ (可选)
- Nacos 2.0+ (可选)

### 本地运行

1. **克隆项目**

```bash
git clone <repository-url>
cd structure-gateway
```

2. **配置环境变量**（可选，使用默认值可跳过）

```bash
# Nacos 配置
export NACOS_SERVER=nacos.structured.cn:8848
export NACOS_NAMESPACE=5a4e4c1f-beda-4ae5-a3d7-428950e7473b
export NACOS_GROUP=dev
export NACOS_DISCOVERY_ENABLE=false
export NACOS_CONFIG_ENABLE=false
export NACOS_USERNAME=structure
export NACOS_PASSWORD=structure

# Redis 配置
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=123456
export REDIS_DATABASE=0

# RabbitMQ 配置
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=root
export RABBITMQ_PASSWORD=123456
export RABBITMQ_VHOST=/
```

3. **启动服务**

```bash
mvn clean package
java -jar target/gateway.jar
```

或使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

4. **验证服务**

服务默认运行在 `http://localhost:18000`

```bash
curl http://localhost:18000/actuator/health
```

## 统一响应格式

### 响应结构

所有 API 响应都使用统一的 JSON 格式：

```json
{
    "code": "SUCCESS",
    "message": "操作成功",
    "success": true,
    "data": {},
    "timestamp": 1779374183584
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | String | 业务状态码 |
| message | String | 响应消息 |
| success | Boolean | 是否成功 |
| data | Object | 响应数据 |
| timestamp | Long | 时间戳（毫秒） |

## 错误码说明

### 业务错误码

网关定义了以下业务错误码：

| HTTP状态 | 错误码 | 说明 | 触发场景 |
|---------|--------|------|----------|
| 400 | BAD_REQUEST | 请求参数错误 | 参数校验失败、格式错误 |
| 401 | INVALID_AUTHENTICATION | 认证信息无效 | Token格式错误、无法解析 |
| 401 | UNAUTHORIZED | 未授权 | Token缺失或无效 |
| 403 | FORBIDDEN | 禁止访问 | 权限不足、租户被禁用 |
| 404 | NOT_FOUND | 资源未找到 | 路由未配置、服务不可用 |
| 429 | TOO_MANY_REQUESTS | 请求过于频繁 | QPS/日/月限流触发 |
| 500 | INTERNAL_ERROR | 服务器内部错误 | 系统异常 |
| 502 | BAD_GATEWAY | 网关错误 | 后端服务返回错误响应 |
| 503 | SERVICE_UNAVAILABLE | 服务不可用 | 后端服务宕机 |
| 504 | GATEWAY_TIMEOUT | 网关超时 | 后端服务响应超时 |

### 错误响应示例

#### 400 Bad Request - 参数错误

```json
{
    "code": "BAD_REQUEST",
    "message": "X-Request-Id is required",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

#### 401 Invalid Authentication - 认证信息无效

```json
{
    "code": "INVALID_AUTHENTICATION",
    "message": "Cannot convert access token to JSON",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

#### 401 Unauthorized - 未授权

```json
{
    "code": "UNAUTHORIZED",
    "message": "Authorization header is required",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

#### 403 Forbidden - 禁止访问

```json
{
    "code": "FORBIDDEN",
    "message": "租户不存在或未配置套餐",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

#### 404 Not Found - 资源未找到

```json
{
    "code": "NOT_FOUND",
    "message": "Service not found",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

#### 429 Too Many Requests - 请求过于频繁

```json
{
    "code": "TOO_MANY_REQUESTS",
    "message": "QPS limit exceeded",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

#### 504 Gateway Timeout - 网关超时

```json
{
    "code": "GATEWAY_TIMEOUT",
    "message": "Gateway timeout",
    "success": false,
    "data": null,
    "timestamp": 1779374183584
}
```

### 过滤器错误码映射

| 过滤器 | HTTP状态 | 错误码 | 典型错误消息 |
|--------|---------|--------|-------------|
| TraceHeaderFilter | 400 | BAD_REQUEST | X-Request-Id is required |
| ReplayAttackPreventionFilter | 400 | BAD_REQUEST | X-Timestamp is required |
| TokenVerificationFilter | 401 | UNAUTHORIZED | Authorization header is required |
| TokenVerificationFilter | 401 | INVALID_AUTHENTICATION | Cannot convert access token to JSON |
| TenantRateLimitFilter | 403 | FORBIDDEN | 租户不存在或未配置套餐 |
| TenantRateLimitFilter | 429 | TOO_MANY_REQUESTS | QPS/Daily/Monthly limit exceeded |
| GlobalExceptionHandler | 404 | NOT_FOUND | Service not found |
| GlobalExceptionHandler | 504 | GATEWAY_TIMEOUT | Gateway timeout |
| GlobalExceptionHandler | 500 | INTERNAL_ERROR | Internal server error |

## 配置说明

### 网关配置 (structure.gateway)

```yaml
structure:
  gateway:
    # Token 验证配置
    token-check:
      enabled: true                    # 是否启用
      min-token-length: 20             # Token 最小长度
    # 租户识别配置
    tenant-identification:
      enabled: true                    # 是否启用
      default-tenant-id: default       # 默认租户 ID
      max-tenant-id-length: 64         # 租户 ID 最大长度
    # 限流配置
    rate-limit:
      enabled: true                    # 是否启用
      whitelist-tenants:               # 白名单租户
        - "1"
        - "2"
      daily-limit-expire-seconds: 86400    # 日限流 Key 过期时间（秒）
      monthly-limit-expire-seconds: 2678400 # 月限流 Key 过期时间（秒）
    # 重放攻击防护配置
    replay-check:
      enabled: true                    # 是否启用
      timestamp-tolerance-ms: 300000   # 时间戳容差（毫秒，默认5分钟）
      require-timestamp: true          # 是否要求时间戳
      require-signature: false         # 是否要求签名
      require-nonce: false             # 是否要求 Nonce
      min-nonce-length: 8              # Nonce 最小长度
      max-nonce-length: 64             # Nonce 最大长度
      min-signature-length: 32         # 签名最小长度
      nonce-expire-minutes: 10         # Nonce 过期时间（分钟）
    # 链路追踪配置
    trace-config:
      enabled: true                    # 是否启用
      require-request-id: true         # 是否要求请求 ID
    # 排除路径（跳过 Token、限流等检查，但仍执行 Trace 和 Replay 检查）
    excluded-paths:
      - /auth/**
      - /actuator/**
    # 安全检查排除路径（仍需验证 Trace 和 Replay）
    security-excluded-paths:
      - /user/**
```

### 重放攻击防护配置说明

#### 三种验证机制对比

| 配置项 | 功能 | 依赖 | 独立验证 | 适用场景 |
|--------|------|------|----------|----------|
| `require-timestamp: true` | 验证请求时间是否在有效范围内（默认±5分钟） | 无外部依赖 | ✅ 完全独立 | 基础防重放 |
| `require-nonce: true` | 验证随机字符串是否已被使用过 | Redis | ✅ 完全独立 | 防止请求重放 |
| `require-signature: true` | 验证请求内容是否被篡改（HMAC-SHA256） | 无外部依赖 | ⚠️ 逻辑依赖 | 防篡改 |

#### 依赖关系详解

**1. Timestamp 验证 - 完全独立**
- 只验证请求时间是否在允许范围内
- 不依赖其他两个配置
- 不依赖外部服务（仅依赖系统时间）

**2. Nonce 验证 - 独立检查**
- 只检查 nonce 值是否已被使用过
- 不依赖 timestamp 和 signature
- 需要 Redis 存储已使用的 nonce（默认10分钟过期）

**3. Signature 验证 - 逻辑依赖**
- 使用 timestamp 和 nonce 作为签名内容的一部分
- 但不强制要求它们必须存在（可以为 null）
- 不依赖外部服务，但依赖 secret-key 配置

#### 推荐的配置组合

| 场景 | require-timestamp | require-nonce | require-signature | 安全性 | 说明 |
|------|-------------------|---------------|-------------------|--------|------|
| 基础防护 | ✅ true | ❌ false | ❌ false | ⭐⭐ | 仅防止过期请求 |
| 防重放 | ✅ true | ✅ true | ❌ false | ⭐⭐⭐⭐ | 防止请求重复发送 |
| 防篡改 | ✅ true | ❌ false | ✅ true | ⭐⭐⭐⭐ | 防止请求内容被修改 |
| 高安全 | ✅ true | ✅ true | ✅ true | ⭐⭐⭐⭐⭐ | 同时防重放和防篡改 |

### 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-center
          uri: http://localhost:18002
          predicates:
            - Path=/auth/**
          filters:
            - StripPrefix=1
        - id: user-center
          uri: http://localhost:18001
          predicates:
            - Path=/user/**
          filters:
            - StripPrefix=1
```

## 请求头说明

| Header 名称 | 说明 | 是否必填 | 示例 |
|------------|------|---------|------|
| Authorization | Bearer Token | 是（非排除路径） | `Bearer eyJhbGciOiJIUzI1NiIs...` |
| X-Tenant-Id | 租户 ID | 否（有默认值） | `tenant-123` |
| X-Request-Id | 请求追踪 ID | 否（可配置必填） | `550e8400-e29b-41d4-a716-446655440000` 或其他自定义字符串 |
| X-Timestamp | 请求时间戳（毫秒） | 是（重放检查启用时） | `1715345678000` |
| X-Nonce | 随机字符串 | 是（重放检查启用且 require-nonce=true） | `abc123xyz` |
| X-Signature | 请求签名 | 是（重放检查启用且 require-signature=true） | `sha256hash` |

## 签名算法说明

### 签名算法（HMAC-SHA256）

当 `structure.gateway.replay-check.require-signature=true` 时，请求必须包含有效的 `X-Signature` 请求头。

#### 签名内容组成

```
签名内容 = method + path + timestamp + nonce
```

- `method`: HTTP 请求方法（大写），如 `GET`、`POST`
- `path`: 请求路径，如 `/user/profile`
- `timestamp`: `X-Timestamp` 请求头的值
- `nonce`: `X-Nonce` 请求头的值（如果启用了 nonce 检查）

#### 使用 HMAC-SHA256 签名

使用配置的 `secret-key` 对签名内容进行 HMAC-SHA256 哈希，结果转换为十六进制字符串（小写）。

#### 示例

假设：
- 请求方法：`GET`
- 请求路径：`/user/profile`
- X-Timestamp：`1715345678000`
- X-Nonce：`abc123xyz`
- 密钥：`your-secret-key-change-in-production`

拼接后的内容：
```
GET/user/profile1715345678000abc123xyz
```

使用 HMAC-SHA256 签名后的结果就是 `X-Signature` 的值。

#### 配置说明

在 `application.yaml` 中配置：
```yaml
structure:
  gateway:
    replay-check:
      enabled: true
      require-signature: true  # 启用签名验证
      secret-key: your-secret-key-change-in-production  # 签名密钥
```

### Nonce 说明

**Nonce**（Number used once）是一个只使用一次的随机字符串，用于防止重放攻击：

- **作用**：每个请求使用一个唯一的 nonce，服务器记录已使用过的 nonce
- **防止重放**：即使攻击者截获了请求，也无法重新发送，因为 nonce 已被标记为已使用
- **配合时间戳**：结合 `X-Timestamp` 一起使用，提供双重防护
- **存储**：存储在 Redis 中，默认过期时间为 10 分钟（可配置）

**Nonce 生成建议**：
- 长度：8-64 个字符
- 建议使用 UUID 或安全的随机字符串生成器
- 示例：`550e8400-e29b-41d4-a716-446655440000`

## 限流配置

### Redis 中的套餐数据结构

Key: `gateway:tenant:package:{tenantId}`

Value (JSON 格式):

```json
{
  "rateLimitEnabled": true,
  "rateLimitRules": {
    "qps": 100,
    "dailyLimit": 10000,
    "monthlyLimit": 300000
  }
}
```

### 限流 Key 格式

- QPS 限流: `gateway:rate:limit:qps:{tenantId}` (过期时间: 1秒)
- 日限流: `gateway:rate:limit:daily:{yyyyMMdd}:{tenantId}` (过期时间: 1天)
- 月限流: `gateway:rate:limit:monthly:{yyyyMM}:{tenantId}` (过期时间: 31天)

### 套餐变更消息

通过 RabbitMQ 接收租户套餐变更消息，消息格式:

```json
{
  "tenantId": "1",
  "operation": "UPDATE",
  "packageData": {
    "rateLimitEnabled": true,
    "rateLimitRules": {
      "qps": 200,
      "dailyLimit": 20000,
      "monthlyLimit": 600000
    }
  }
}
```

## 开发指南

### 添加新的过滤器

1. 创建过滤器类，实现 `GlobalFilter` 和 `Ordered` 接口

```java
@Component
public class MyCustomFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 前置处理
        ServerHttpRequest request = exchange.getRequest();
        // 自定义逻辑
        
        // 继续过滤器链
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 设置执行顺序
    }
}
```

2. 在 `StructureGatewayProperties` 中添加配置（如需要）

3. 在 `application.yaml` 中配置默认值

### 本地调试

1. **禁用 Nacos**（使用本地配置）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
```

2. **配置本地路由**

在 `application.yaml` 中配置指向本地服务的路由

3. **启动依赖服务**

确保 Redis 正在运行

4. **启动网关**

使用 IDE 或 Maven 启动

### 测试

使用 curl 或 Postman 测试网关功能：

```bash
# 测试排除路径（无需认证）
curl http://localhost:18000/auth/login

# 测试需要认证的路径
curl -H "Authorization: Bearer your-token-here" \
     -H "X-Tenant-Id: 1" \
     http://localhost:18000/user/profile

# 测试链路追踪（需要 X-Request-Id）
curl -H "Authorization: Bearer your-token-here" \
     -H "X-Request-Id: 550e8400-e29b-41d4-a716-446655440000" \
     -H "X-Timestamp: $(date +%s000)" \
     http://localhost:18000/user/profile

# 测试限流触发
for i in {1..100}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
       -H "Authorization: Bearer test-token" \
       -H "X-Tenant-Id: 1" \
       http://localhost:18000/user/profile
done
```

## 监控

### Actuator 端点

- `/actuator/health` - 健康检查
- `/actuator/info` - 服务信息
- `/actuator/gateway` - 网关信息

### 日志级别

```yaml
logging:
  level:
    cn.structured.cloud.gateway: DEBUG
    org.springframework.cloud.gateway: INFO
```

### 请求日志

网关自动记录所有请求的详细信息：

```
[GATEWAY-IN] GET /user/profile - Headers: {content-type:[application/json]}
[GATEWAY-OUT] GET /user/profile - Status: 200 - Duration: 150ms - Route: user-center -> http://localhost:18001
[GATEWAY-ERROR] GET /user/nonexistent - Status: 404 - Route: user-center -> http://localhost:18001
```

## 部署

### Docker 部署

```dockerfile
FROM openjdk:8-jre-alpine
WORKDIR /app
COPY target/gateway.jar app.jar
EXPOSE 18000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes 部署

参考 `.github/workflows/build-gateway-and-push.yml` 中的 CI/CD 流程

## 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件
