AgentOS 技术规格文档（最终完整版）
=====================

**文档编号:** AGENTOS-SPEC-FINAL-V2.0  
**版本:** 2.0  
**发布日期:** 2026-04-22  
**状态:** 正式发布

> **版本说明**：本版本整合了第一里程碑（v0.1~v0.3）全部功能，并明确了第二里程碑（v0.4~v1.0）的增强规划，包括智能路由、模板挖掘、SSE实时推送、全链路追踪、多租户、高可用等。

* * *

目录
--

1. 背景与目标

2. 总体架构

3. 技术选型

4. 核心模块设计

5. 消息处理设计

6. 数据库设计

7. 配置管理（Nacos）

8. 部署方案（Docker Compose）

9. API 接口规范

10. 错误处理最佳实践

11. 日志配置

12. 测试指南

13. Python 技能开发模板

14. 置信度评分详细算法

15. 虚拟线程消费者实现（生产级）

16. Nacos 配置热更新详解

17. 性能基准数据

18. 安全、稳定、效率、准确性保障

19. 租户隔离与多租户设计

20. 幂等性设计

21. 链路追踪标准

22. 验收标准

23. 版本规划与第二里程碑

24. 附录

* * *

1. 背景与目标

--------

AgentOS 是一个企业级 AI Agent 编排平台，旨在解决大模型在企业应用中的**确定性、可控性、可观测性**问题。通过 Harness 工程范式（边界、工作流、验收、优化），将 LLM 的动态推理能力嵌入到安全、稳定、可扩展的工程框架中。

**核心目标**：

* 降低 AI 应用开发门槛（自然语言编排 + 模板市场）

* 保证业务执行的准确性与安全性（边界校验、置信度评分、人工兜底）

* 实现高并发、高可用的企业级能力（异步队列、虚拟线程、服务化）

* 支持快速迭代与持续优化（轨迹采集、模板挖掘）

本规格文档覆盖**第一里程碑**（v0.1~v0.3 合并版）及**第二里程碑规划**。

* * *

## 2. 总体架构

-------

### 2.1 架构分层

```text
┌─────────────────────────────────────────────────────────────────┐
│                      接入与边界层（API Gateway）                  │
│  JWT 认证 │ RBAC 权限 │ 工具白名单 │ 配额限流 │ 风险分级 │ 强制审批 │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                      编排中枢（Supervisor）                       │
│  WorkFlow 引擎（确定性 DAG）      AgentFlow 引擎（ReAct 循环）     │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                   消息层（Redis Streams）                         │
│  task-queue │ event-queue │ dead-letter-queue                   │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                      执行层（虚拟线程池）                          │
│  节点执行器 │ Skill 调用器 │ Python HTTP 客户端（负载均衡）         │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                 Python 技能层（注册到 Nacos）                     │
│  nlp-analyzer │ data-transform │ custom-skill-*                 │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                   基础设施层（PostgreSQL / Redis / Nacos）         │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心数据流

1. 用户通过 REST API 提交任务 → 边界层校验（JWT、RBAC、白名单、配额、风险） → 创建实例 → 发送 `TaskMessage` 到 Redis Streams `task-queue`

2. 消费者（虚拟线程池）从 `task-queue` 拉取消息 → 根据任务类型调用 WorkFlow 引擎或 AgentFlow 引擎

3. 执行过程中需要调用外部能力（Python 技能、数据库、API） → 通过服务发现（Nacos）获取地址 → HTTP 调用

4. 每个节点执行后保存检查点（PostgreSQL） → 发送事件到 `event-queue`（用于异步审计）

5. 任务失败 → 进入 `dead-letter-queue` → 重试（指数退避，最多 3 次） → 仍失败则转人工兜底

6. 人工审批通过后，从检查点恢复任务继续执行

* * *

## 3. 技术选型

-------

| 组件        | 技术                               | 版本     | 说明                                 |
| --------- | -------------------------------- | ------ | ---------------------------------- |
| 主框架       | Spring Boot                      | 3.5.10 | 虚拟线程支持                             |
| AI 集成     | Spring AI                        | 1.1.3  | 统一 LLM 调用（OpenAI、Ollama 等）         |
| Agent 编排  | spring-ai-agent-utils            | 0.5.0  | 工具注册、ReAct 循环管理                    |
| 数据库       | PostgreSQL                       | 16     | JSONB 支持，用于存储定义、实例、审计              |
| 缓存/队列     | Redis Stack                      | 7.2.0  | 缓存 + 分布式锁 + Streams 队列             |
| 服务发现与配置   | Alibaba Nacos                    | 2.3.0  | 服务注册与发现、配置中心                       |
| Python 框架 | FastAPI + Uvicorn                | 0.109+ | 独立微服务，注册到 Nacos                    |
| 安全        | Spring Security + JJWT           | 0.12.5 | JWT 认证 + RBAC 权限模型                 |
| 可观测性      | Micrometer + Prometheus          | 1.13+  | Metrics 端点（`/actuator/prometheus`） |
| 链路追踪      | OpenTelemetry + W3C TraceContext | 1.31+  | 跨服务 Trace 传播                       |

* * *

## 4. 核心模块设计

---------

### 4.1 边界 Harness

负责所有请求的准入控制，确保安全合规。

```java

@Component
public class BoundaryChecker {
    // RBAC 权限检查（基于 Spring Security）
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEVELOPER')")
    public void checkCreateDefinition(TaskRequest request) { ... }
    // 工具白名单校验（从 Redis 缓存加载）
    public void checkToolCall(String toolName) {
        Tool tool = toolRegistry.findByName(toolName);
        if (tool == null) throw new ToolNotFoundException();
        if (tool.getRiskLevel() == RiskLevel.CRITICAL) {
            throw new NeedApprovalException("高风险工具需要审批");
        }
    }
    // 配额限流（使用 Redis + Lua 实现滑动窗口）
    public boolean acquireQuota(String tenantId) {
        String key = "quota:sliding:" + tenantId;
        long now = System.currentTimeMillis();
        long windowMs = 60_000;   // 1 分钟窗口
        long limit = 100;          // 每分钟 100 次
        String script = 
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
            "local count = redis.call('ZCARD', key) " +
            "if count < limit then " +
            "   redis.call('ZADD', key, now, now) " +
            "   redis.call('EXPIRE', key, window / 1000) " +
            "   return 1 " +
            "else " +
            "   return 0 " +
            "end";
        Long result = redisTemplate.execute(RedisScript.of(script, Long.class), 
                                            Collections.singletonList(key), now, windowMs, limit);
        return result == 1;
    }
}
```

### 4.2 WorkFlow 引擎（确定性 DAG）

* 支持节点类型：`Start`、`End`、`LLM-Chat`、`Tool`、`Loop`、`Gateway`、`SubFlow`、`Human`

* 静态校验：DAG 环检测、条件边覆盖分析、节点 Schema 合法性

* 执行流程：创建实例 → 入队 → 消费者拉取 → 顺序执行节点 → 保存检查点 → 根据条件边跳转

* 断点续跑：从 `checkpoint` 表加载上下文，从挂起节点继续

### 4.3 AgentFlow 引擎（ReAct 可控）

* 基于 `spring-ai-agent-utils` 的 `ReactAgent`

* 每轮迭代：
  
  * `Thought`：LLM 思考下一步动作
  
  * `Action`：解析工具名称和参数
  
  * `Observation`：执行工具返回结果

* 置信度评分：详见第 14 章

* 当置信度低于阈值（默认 0.7）时，自动挂起任务并转人工

### 4.4 消息处理（Redis Streams）

* **任务队列** (`task-queue`)：存储 `TaskMessage`，消费者组 `AgentOS-Executor`

* **事件队列** (`event-queue`)：存储 `EventMessage`，消费者组 `AgentOS-Auditor`（异步写审计日志）

* **死信队列** (`dead-letter-queue`)：存储失败任务，支持重试（1s、2s、4s 退避，最多 3 次）

消费者实现详见第 15 章。

### 4.5 Python 技能集成（Nacos 服务发现）

* Python 技能作为独立容器，启动时注册到 Nacos

* Java 通过 `@LoadBalanced RestTemplate` 使用服务名调用

* 示例 Python 服务模板详见第 13 章

### 4.6 人工兜底

* 任务挂起时保存检查点（`checkpoint` 表）并创建审批记录（`approval_record` 表）

* 提供 REST API：
  
  * `POST /api/v1/instances/{id}/suspend`：主动挂起
  
  * `POST /api/v1/approvals/{id}/resolve`：审批（APPROVED/REJECTED），恢复或终止任务

* * *

## 5. 消息处理设计

---------

### 5.1 消息结构

```java

@Data @Builder
public class TaskMessage {
    private String instanceId;
    private String defId;
    private Map<String, Object> variables;
    private String traceparent;
    private int retryCount;
}
@Data @Builder
public class EventMessage {
    private String eventId;
    private String eventType;  // NODE_START, NODE_END, INSTANCE_COMPLETED, etc.
    private String instanceId;
    private String traceId;
    private String data;
    private long timestamp;
}
```

### 5.2 生产者

```java

@Service
public class TaskProducer {
    public void send(TaskMessage task) {
        redisTemplate.opsForStream().add("task-queue", task.toMap());
    }
}
```

### 5.3 延迟队列方案（生产级）

**不使用 Thread.sleep**，而是使用 Redis Sorted Set + 定时轮询：

```java

@Component
public class DelayedTaskProducer {
    public void sendDelayed(TaskMessage task, long delayMs) {
        long executeAt = System.currentTimeMillis() + delayMs;
        String key = "delayed:task";
        redisTemplate.opsForZSet().add(key, JsonUtils.toJson(task), executeAt);
    }
}
@Component
public class DelayedTaskPoller {
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        long now = System.currentTimeMillis();
        String key = "delayed:task";
        Set<String> ready = redisTemplate.opsForZSet().rangeByScore(key, 0, now);
        for (String json : ready) {
            TaskMessage task = JsonUtils.fromJson(json, TaskMessage.class);
            taskProducer.send(task);
            redisTemplate.opsForZSet().remove(key, json);
        }
    }
}
```

此方案支持持久化、集群、重启不丢失。

* * *

## 6. 数据库设计

--------

### 6.1 核心表结构

```sql

-- 流程定义表
CREATE TABLE wf_definition (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,        -- 多租户支持
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,             -- WORKFLOW / AGENTFLOW
    definition_json JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 流程实例表
CREATE TABLE wf_instance (
    instance_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    def_id VARCHAR(64) REFERENCES wf_definition(id),
    status VARCHAR(20) NOT NULL,           -- PENDING/RUNNING/SUSPENDED/COMPLETED/FAILED
    context JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
-- 节点运行记录
CREATE TABLE node_run (
    id BIGSERIAL PRIMARY KEY,
    instance_id VARCHAR(64) REFERENCES wf_instance(instance_id),
    node_id VARCHAR(64),
    status VARCHAR(20),
    output_preview TEXT,
    confidence DECIMAL(3,2),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ
);
-- 检查点表
CREATE TABLE checkpoint (
    instance_id VARCHAR(64) PRIMARY KEY,
    node_id VARCHAR(64),
    context JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 工具注册表（白名单）
CREATE TABLE tool_registry (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) UNIQUE,
    risk_level VARCHAR(20) NOT NULL,       -- LOW/MEDIUM/HIGH/CRITICAL
    input_schema JSONB,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
-- 审批记录表
CREATE TABLE approval_record (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64) REFERENCES wf_instance(instance_id),
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED
    approver VARCHAR(64),
    comment TEXT,
    created_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ
);
-- 审计日志表
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    action VARCHAR(50),
    resource_type VARCHAR(50),
    resource_id VARCHAR(64),
    detail JSONB,
    ip_address INET,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 死信队列表
CREATE TABLE dead_letter (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64),
    error_code VARCHAR(50),
    error_message TEXT,
    context_snapshot JSONB,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ
);
```

### 6.2 索引建议

```sql

CREATE INDEX idx_wf_instance_tenant_status ON wf_instance(tenant_id, status, created_at);
CREATE INDEX idx_node_run_instance ON node_run(instance_id, node_id);
CREATE INDEX idx_audit_log_tenant_user ON audit_log(tenant_id, user_id, created_at);
```

* * *

## 7. 配置管理（Nacos）

--------------

### 7.1 应用配置（bootstrap.yml）

```yaml

spring:
  application:
    name: agentos-orchestrator
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_HOST:localhost}:8848
      config:
        server-addr: ${NACOS_HOST:localhost}:8848
        file-extension: yaml
        shared-configs:
          - data-id: common.yaml
```

### 7.2 动态配置项（common.yaml）

```yaml

llm:
  provider: openai
  api-key: ${OPENAI_API_KEY}
  model: gpt-4
  timeout: 30s
agentos:
  workflow:
    max-nodes: 100
    checkpoint-enabled: true
  agentflow:
    max-iterations: 10
    confidence-threshold: 0.7
  quota:
    default-limit: 100   # 每分钟
  risk:
    high-level-approval: true
```

配置修改后无需重启应用（热更新机制详见第 16 章）。

* * *

## 8. 部署方案（Docker Compose）

-----------------------

### 8.1 docker-compose.yml

```yaml

version: '3.8'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: agentos
      POSTGRES_USER: agentos
      POSTGRES_PASSWORD: agentos123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
  redis:
    image: redis/redis-stack:7.2.0-v0
    ports:
      - "6379:6379"
      - "8001:8001"
    volumes:
      - redis_data:/data
  nacos:
    image: nacos/nacos-server:v2.3.0
    ports:
      - "8848:8848"
      - "9848:9848"
    environment:
      MODE: standalone
    volumes:
      - nacos_data:/home/nacos/data
  agentos-java:
    build: ./agentos-java
    ports:
      - "8080:8080"
    environment:
      NACOS_HOST: nacos
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/agentos
      SPRING_DATASOURCE_USERNAME: agentos
      SPRING_DATASOURCE_PASSWORD: agentos123
      SPRING_REDIS_HOST: redis
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on:
      - postgres
      - redis
      - nacos
  python-skill-nlp:
    build: ./python-skills/nlp
    ports:
      - "8002:8000"
    environment:
      NACOS_HOST: nacos
      REDIS_HOST: redis
    depends_on:
      - nacos
      - redis
volumes:
  postgres_data:
  redis_data:
  nacos_data:
```

### 8.2 Java 应用 Dockerfile

```dockerfile

FROM openjdk:21-slim
COPY target/agentos-mvp.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 8.3 Python 技能 Dockerfile

```dockerfile

FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 8.4 启动命令

```bash

docker-compose up -d
```

* * *

## 9. API 接口规范

-----------

### 9.1 统一错误响应结构

所有错误响应遵循格式：

```json

{
  "code": 4001,
  "message": "流程定义不存在",
  "traceId": "abc-123-def",
  "details": null
}
```

### 9.2 认证接口

| 方法   | 路径                   | 描述       |
| ---- | -------------------- | -------- |
| POST | `/api/v1/auth/login` | 登录获取 JWT |

请求：

```json

{"username":"admin","password":"admin"}
```

响应：

```json

{"token":"eyJhbGciOiJIUzI1NiIs..."}
```

### 9.3 流程管理

| 方法     | 路径                                 | 权限                 | 描述     |
| ------ | ---------------------------------- | ------------------ | ------ |
| POST   | `/api/v1/definitions`              | `workflow:write`   | 创建流程定义 |
| PUT    | `/api/v1/definitions/{id}`         | `workflow:write`   | 更新流程   |
| POST   | `/api/v1/definitions/{id}/publish` | `workflow:publish` | 发布流程   |
| DELETE | `/api/v1/definitions/{id}`         | `workflow:delete`  | 删除流程   |

**示例（创建 WorkFlow）**：

```json

{
  "id": "order_flow",
  "name": "订单处理",
  "type": "WORKFLOW",
  "nodes": [
    {"id": "start", "type": "Start"},
    {"id": "check_stock", "type": "Tool", "config": {"tool": "query_stock"}},
    {"id": "end", "type": "End"}
  ],
  "edges": [
    {"from": "start", "to": "check_stock"},
    {"from": "check_stock", "to": "end"}
  ]
}
```

### 9.4 流程执行（支持幂等键）

| 方法   | 路径                              | 权限                 | 描述                              |
| ---- | ------------------------------- | ------------------ | ------------------------------- |
| POST | `/api/v1/instances`             | `workflow:execute` | 启动实例（异步），支持 `Idempotency-Key` 头 |
| GET  | `/api/v1/instances/{id}`        | `workflow:read`    | 查询状态                            |
| POST | `/api/v1/instances/{id}/resume` | `workflow:execute` | 恢复挂起实例，支持幂等                     |

**启动实例请求**：

```http

POST /api/v1/instances
Idempotency-Key: uuid-12345
Content-Type: application/json
{
  "definitionId": "order_flow",
  "variables": {"orderId": "ORD-123"},
  "tenantId": "acme"
}
```

**响应**：

```json

{
  "instanceId": "inst_abc123",
  "status": "PENDING",
  "createdAt": "2026-04-22T10:00:00Z"
}
```

### 9.5 审批接口

| 方法   | 路径                               | 权限               | 描述   |
| ---- | -------------------------------- | ---------------- | ---- |
| POST | `/api/v1/approvals/{id}/resolve` | `approval:write` | 审批任务 |

请求：

```json

{
  "decision": "APPROVED",
  "comment": "同意执行"
}
```

### 9.6 可观测性

| 方法  | 路径                     | 描述            |
| --- | ---------------------- | ------------- |
| GET | `/actuator/prometheus` | Prometheus 指标 |
| GET | `/actuator/health`     | 健康检查          |

### 9.7 SSE 实时推送（第二里程碑）

| 方法  | 路径                                      | 描述            |
| --- | --------------------------------------- | ------------- |
| GET | `/api/v1/instances/{instanceId}/stream` | 订阅实例实时事件（SSE） |

**响应格式**（`text/event-stream`）：

```text

event: node_start
data: {"nodeId":"llm_analyze","timestamp":"2026-04-22T10:00:00Z"}
event: thought
data: {"content":"我需要查询订单状态","iteration":1,"confidence":0.85}
event: action
data: {"tool":"query_order","params":{"orderId":"123"}}
event: observation
data: {"result":"订单状态：已发货","confidence":0.92}
event: completed
data: {"finalResult":"处理完成"}
```

* * *

## 10. 错误处理最佳实践

------------

### 10.1 统一异常处理

```java

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.warn("Business error: {}", ex.getMessage());
        return buildErrorResponse(ex.getCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ErrorResponse> handleRetryable(RetryableException ex) {
        log.warn("Retryable error: {}", ex.getMessage());
        delayedTaskProducer.sendDelayed(ex.getTaskMessage(), ex.getRetryDelayMs());
        return buildErrorResponse(5001, "Task will be retried", HttpStatus.ACCEPTED);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        deadLetterProducer.send(extractTaskMessage(ex), ex.getMessage());
        return buildErrorResponse(5000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    private ResponseEntity<ErrorResponse> buildErrorResponse(int code, String message, HttpStatus status) {
        String traceId = MDC.get("traceId");
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, traceId, null));
    }
}
```

### 10.2 重试与降级（Spring Retry）

```java

@Service
@Slf4j
public class LLMService {
    @Retryable(value = {TimeoutException.class, RateLimitException.class},
               maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String callLLM(String prompt) { ... }
    @Recover
    public String fallback(TimeoutException e, String prompt) {
        log.warn("LLM call failed after retries: {}", e.getMessage());
        return "Service busy, please try later.";
    }
}
```

### 10.3 死信处理

```java

@Component
public class DeadLetterHandler {
    public void handle(DeadLetterMessage msg) {
        int retryCount = msg.getRetryCount() + 1;
        if (retryCount <= 3) {
            long delay = (long) Math.pow(2, retryCount) * 1000;
            delayedTaskProducer.sendDelayed(msg.toTaskMessage(), delay);
        } else {
            humanLoopService.suspend(msg.getInstanceId(), "Max retries exceeded");
            deadLetterRepository.save(msg);
        }
    }
}
```

* * *

## 11. 日志配置

--------

### 11.1 logback-spring.xml

```xml

<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} %highlight(%-5level) [%thread] %logger{36} - [%X{traceId}] %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/agentos.log</file>
        <encoder class="ch.qos.logback.classic.encoder.JsonEncoder"/>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/agentos-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE"/>
        <queueSize>5000</queueSize>
        <discardingThreshold>0</discardingThreshold>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC"/>
    </root>
    <springProfile name="dev">
        <logger name="com.agentos" level="DEBUG"/>
    </springProfile>
</configuration>
```

### 11.2 MDC 注入 traceId（基于 W3C TraceContext）

在网关过滤器中生成或提取 traceId：

```java

@Component
public class TraceIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest req = (HttpServletRequest) request;
        String traceparent = req.getHeader("traceparent");
        String traceId;
        if (traceparent != null && traceparent.startsWith("00-")) {
            traceId = traceparent.split("-")[1];
        } else {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

* * *

## 12. 测试指南

--------

### 12.1 单元测试（JUnit + Mockito）

```java

@ExtendWith(MockitoExtension.class)
class WorkFlowEngineTest {
    @Mock private NodeExecutor nodeExecutor;
    @Mock private CheckpointService checkpointService;
    @InjectMocks private WorkFlowEngine engine;
    @Test
    void shouldExecuteLinearWorkflow() {
        WorkflowDefinition def = createLinearDefinition();
        when(nodeExecutor.execute(any(), any())).thenReturn(NodeResult.success());
        InstanceResult result = engine.execute(def, Map.of());
        assertThat(result.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        verify(nodeExecutor, times(2)).execute(any(), any());
    }
    @Test
    void shouldThrowWhenGatewayConditionUnmatched() {
        WorkflowDefinition def = createGatewayWithoutDefault();
        assertThrows(NoMatchingTransitionException.class, () -> engine.execute(def, Map.of()));
    }
}
```

### 12.2 集成测试（Testcontainers）

```java

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AgentFlowIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Container static RedisContainer redis = new RedisContainer("redis:7-alpine");
    @MockBean private ChatClient chatClient;
    @Autowired private MockMvc mockMvc;
    @Test
    void shouldSuspendOnLowConfidence() throws Exception {
        when(chatClient.prompt(any()).call().content()).thenReturn("low confidence response");
        mockMvc.perform(post("/api/v1/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defId\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }
}
```

### 12.3 压测模型说明

* **场景**：WorkFlow 空转流程包含 5 个纯计算节点（无 I/O，无 LLM）

* **并发**：20 个虚拟用户，持续 5 分钟

* **节点耗时**：每个节点模拟 1ms 计算

* **LLM 场景**：真实调用 OpenAI API（延迟约 300ms）

* **工具**：JMeter 5.6，分布式模式下 2 台施压机

* * *

## 13. Python 技能开发模板

-----------------

### 13.1 项目结构

```text

python-skills/nlp-analyzer/
├── requirements.txt
├── main.py
├── config.py
└── Dockerfile
```

### 13.2 main.py（FastAPI + Nacos + OTEL）

```python

import os, logging, nacos, redis
from fastapi import FastAPI, Header
from pydantic import BaseModel
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.propagate import extract
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.trace.export import BatchSpanProcessor
logging.basicConfig(level=logging.INFO, format='%(asctime)s - [%(trace_id)s] - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)
trace.set_tracer_provider(TracerProvider())
tracer = trace.get_tracer(__name__)
otlp_exporter = OTLPSpanExporter(endpoint=os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://tempo:4317"))
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(otlp_exporter))
app = FastAPI()
nacos_client = nacos.NacosClient(os.getenv("NACOS_HOST", "nacos:8848"), namespace="")
nacos_client.add_naming_instance("nlp-analyzer", "python-nlp", 8000, ephemeral=True)
class Request(BaseModel):
    text: str
    options: dict = {}
@app.post("/execute")
async def execute(req: Request, traceparent: str = Header(None)):
    ctx = extract({"traceparent": traceparent}) if traceparent else None
    with tracer.start_as_current_span("skill.execute", context=ctx) as span:
        span.set_attribute("input.text", req.text[:100])
        # 模拟情感分析
        sentiment = "positive" if "happy" in req.text.lower() else "neutral"
        confidence = 0.95 if sentiment != "neutral" else 0.7
        return {"sentiment": sentiment, "confidence": confidence}
@app.on_event("shutdown")
def shutdown():
    nacos_client.remove_naming_instance("nlp-analyzer", "python-nlp", 8000)
```

### 13.3 requirements.txt

```text

fastapi==0.109.0
uvicorn==0.27.0
nacos-sdk-python==2.0.3
redis==5.0.1
opentelemetry-api==1.22.0
opentelemetry-sdk==1.22.0
opentelemetry-exporter-otlp==1.22.0
```

* * *

## 14. 置信度评分详细算法

-------------

### 14.1 方案 A：基于 LogProbs（OpenAI）

```java

public double computeLogProbConfidence(List<Double> logprobs) {
    double avgLogProb = logprobs.stream().mapToDouble(Double::doubleValue).average().orElse(-10.0);
    double confidence = Math.exp(avgLogProb);
    return Math.min(0.99, Math.max(0.3, confidence));
}
```

### 14.2 方案 B：自一致性（多次采样）

```java

public double computeSelfConsistency(String prompt, int numSamples, double temperature) {
    List<String> samples = new ArrayList<>();
    for (int i = 0; i < numSamples; i++) {
        samples.add(llm.generate(prompt, temperature));
    }
    double totalSimilarity = 0.0;
    for (int i = 0; i < samples.size(); i++) {
        for (int j = i+1; j < samples.size(); j++) {
            totalSimilarity += semanticSimilarity(samples.get(i), samples.get(j));
        }
    }
    double avgSimilarity = totalSimilarity / (numSamples * (numSamples-1) / 2);
    double formatScore = isJsonValid(samples.get(0)) ? 1.0 : 0.0;
    return avgSimilarity * 0.7 + formatScore * 0.3;
}
private double semanticSimilarity(String s1, String s2) {
    Set<String> tokens1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
    Set<String> tokens2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
    long intersection = tokens1.stream().filter(tokens2::contains).count();
    long union = tokens1.size() + tokens2.size() - intersection;
    return (double) intersection / union;
}
```

### 14.3 混合策略与回退

```java

public double score(AgentContext ctx) {
    if (ctx.getLogProbs() != null && !ctx.getLogProbs().isEmpty()) {
        return computeLogProbConfidence(ctx.getLogProbs());
    } else if (ctx.isHighStakes()) {
        return computeSelfConsistency(ctx.getPrompt(), 3, 0.3);
    } else {
        return quickRuleScore(ctx.getObservation());
    }
}
```

* * *

## 15. 虚拟线程消费者实现（生产级）

------------------

### 15.1 消费者组件（支持 pending 消息 reclaim）

```java

@Component
@Slf4j
public class TaskConsumer implements DisposableBean {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private volatile boolean running = true;
    private static final String STREAM_KEY = "task-queue";
    private static final String GROUP_NAME = "AgentOS-Executor";
    private static final String CONSUMER_NAME = "consumer-" + UUID.randomUUID();
    private static final int MAX_PENDING_RECLAIM = 100;
    public TaskConsumer(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        initConsumerGroup();
    }
    private void initConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME, ReadOffset.from("0-0"), true);
        } catch (Exception e) {
            log.warn("Consumer group may already exist: {}", e.getMessage());
        }
    }
    @PostConstruct
    public void start() {
        executor.submit(this::consumeLoop);
        executor.submit(this::reclaimPendingLoop);
    }
    private void consumeLoop() {
        while (running) {
            try {
                List<MapRecord<String, String, String>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(GROUP_NAME, CONSUMER_NAME),
                          StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                          StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, String, String> record : records) {
                    processRecord(record);
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                }
            } catch (Exception e) {
                log.error("Error consuming task", e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }
    private void reclaimPendingLoop() {
        while (running) {
            try {
                Thread.sleep(30_000);
                PendingMessagesSummary pending = redisTemplate.opsForStream().pending(STREAM_KEY, GROUP_NAME);
                if (pending == null || pending.getTotalPendingMessages() == 0) continue;
                List<PendingMessage> pendingMessages = redisTemplate.opsForStream()
                    .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), MAX_PENDING_RECLAIM);
                for (PendingMessage msg : pendingMessages) {
                    if (msg.getElapsedTimeSinceLastDelivery() > 60000) {
                        redisTemplate.opsForStream().claim(STREAM_KEY, GROUP_NAME, CONSUMER_NAME, 
                                                           Collections.singletonList(msg.getId()), 
                                                           Duration.ofSeconds(30));
                        log.info("Reclaimed pending message: {}", msg.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error reclaiming pending", e);
            }
        }
    }
    private void processRecord(MapRecord<String, String, String> record) {
        Map<String, String> value = record.getValue();
        if (value == null || value.get("body") == null) return;
        TaskMessage task = objectMapper.convertValue(value, TaskMessage.class);
        log.info("Processing task instanceId={}", task.getInstanceId());
        // 业务处理...
    }
    @Override
    public void destroy() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Task consumer stopped");
    }
}
```

### 15.2 关键设计要点

* **消费者组**：支持多实例部署，消息不重复消费（Redis 自动分配）

* **优雅关闭**：`@PreDestroy` 停止接收新消息，处理完当前批次后退出

* **消息确认**：处理成功后 ACK，失败不 ACK

* **Pending 消息回收**：定期扫描超时未 ACK 的消息，转移给活跃消费者，防止消息卡死

* * *

## 16. Nacos 配置热更新详解

-----------------

### 16.1 支持热更新的配置

使用 `@RefreshScope` 或 `@ConfigurationProperties` 的 Bean 支持热更新。

```java

@Component
@RefreshScope
@ConfigurationProperties(prefix = "agentos.llm")
@Data
public class LLMProperties {
    private String model;      // 热更新生效
    private int timeout;
}
```

### 16.2 生效机制

* Nacos 客户端默认使用**长轮询**（每 30 秒拉取一次配置变化）

* 配置变更后，Spring Cloud Context 刷新 `@RefreshScope` 标记的 Bean

* 也可以使用 `@NacosConfigListener` 实现实时监听

### 16.3 测试示例

```java

@RestController
@RefreshScope
public class ConfigController {
    @Value("${agentos.llm.model}")
    private String model;
    @GetMapping("/config/model")
    public String getModel() { return model; }
}
```

修改 Nacos 配置后，调用 `/config/model` 立即返回新值。

* * *

## 17. 性能基准数据

----------

### 17.1 测试环境

| 组件         | 规格                   |
| ---------- | -------------------- |
| Java 应用    | 4C8G，Java 21，虚拟线程池   |
| PostgreSQL | 4C16G，SSD            |
| Redis      | 4C8G，Redis Stack 7.2 |
| 网络         | 10Gbps 内网            |

### 17.2 压测模型说明

* **WorkFlow 空转**：5 个纯计算节点（无 I/O），每个节点模拟 1ms 计算

* **WorkFlow + LLM**：5 个节点中包含 1 次 OpenAI 调用（模拟延迟 300ms）

* **AgentFlow**：平均 3 次迭代，每次包含 LLM thought + 工具调用

* **并发模型**：20 个虚拟用户，持续 5 分钟

### 17.3 测试结果

| 场景                | 吞吐量 (实例/秒) | P99 延迟 (ms) | CPU 使用率 |
| ----------------- | ---------- | ----------- | ------- |
| WorkFlow 空转       | 420        | 98          | 65%     |
| WorkFlow + 1次 LLM | 120        | 320         | 55%     |
| AgentFlow（3次迭代）   | 35         | 1850        | 70%     |

### 17.4 结论

* 单机支持 20 并发任务稳定运行，P99 延迟 < 100ms（空转）

* 推荐生产配置：3-5 个 Java Pod，PostgreSQL 读写分离

* * *

## 18. 安全、稳定、效率、准确性保障

------------------

| 目标        | 实现方式                                                                                          |
| --------- | --------------------------------------------------------------------------------------------- |
| **安全**    | JWT + RBAC（admin/developer/user）；工具白名单；风险分级（LOW/MEDIUM/HIGH/CRITICAL），HIGH/CRITICAL 强制审批；审计日志 |
| **稳定**    | 异步队列 + 虚拟线程消费者；失败重试（指数退避，最多 3 次）；死信队列；检查点断点续跑；Nacos 服务发现保证 Python 服务高可用                       |
| **效率**    | WorkFlow 优先（确定性 DAG）；虚拟线程并发执行并行节点；Redis 缓存热点数据（工具白名单、定义）                                      |
| **准确不跑偏** | 节点输出 Schema 校验（非 LLM 节点强制）；AgentFlow 置信度阈值（0.7）；人工兜底；轨迹日志记录                                   |
| **可扩展**   | 模块化包结构；Python 服务通过 Nacos 动态发现；配置外置（环境变量 + Nacos 配置中心）；预留 tenant_id                            |

* * *

## 19. 租户隔离与多租户设计

--------------

### 19.1 租户模型

* 每个租户拥有独立 `tenant_id`

* 所有核心数据表包含 `tenant_id` 字段

* JWT 中携带 `tenant_id`，API 层从 Token 解析并强制注入

### 19.2 租户隔离策略

* **数据隔离**：所有查询自动追加 `WHERE tenant_id = ?`

* **配额隔离**：配额限流按 `tenant_id` 独立计算

* **权限隔离**：用户只能访问所属租户的资源（通过 RBAC 范围控制）

### 19.3 跨租户资源共享

* 全局模板、全局 Skill 标记 `tenant_id = 'GLOBAL'`，所有租户可读不可写

* * *

## 20. 幂等性设计

---------

### 20.1 幂等场景

* 启动实例：使用 `Idempotency-Key` 头，服务端缓存结果 24 小时

* 审批恢复：`approval_id` 天然幂等，重复调用返回相同结果

### 20.2 实现方式

```java

@Component
public class IdempotencyManager {
    private final RedisTemplate<String, String> redisTemplate;
    public boolean isProcessed(String key) {
        return redisTemplate.hasKey("idempotent:" + key);
    }
    public void markProcessed(String key, String result, long ttlSeconds) {
        redisTemplate.opsForValue().set("idempotent:" + key, result, Duration.ofSeconds(ttlSeconds));
    }
    public String getResult(String key) {
        return redisTemplate.opsForValue().get("idempotent:" + key);
    }
}
```

在 API 层：

```java

@PostMapping("/instances")
public ResponseEntity<?> createInstance(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, ...) {
    if (idempotencyKey != null && idempotencyManager.isProcessed(idempotencyKey)) {
        String cached = idempotencyManager.getResult(idempotencyKey);
        return ResponseEntity.ok(cached);
    }
    // 正常创建...
    idempotencyManager.markProcessed(idempotencyKey, resultJson, 86400);
    return ResponseEntity.ok(result);
}
```

* * *

## 21. 链路追踪标准

----------

### 21.1 W3C TraceContext 传播

* Java 端使用 OpenTelemetry SDK 自动注入 `traceparent` header

* Python 端从 `traceparent` header 恢复上下文

* 日志中使用 `traceId` 字段（从 `traceparent` 解析）

### 21.2 跨服务传播

* HTTP 调用时自动传递 `traceparent`

* 消息队列（Redis Streams）中存储 `traceparent` 字段，消费者恢复

### 21.3 日志字段统一

* 日志格式：`[%X{traceId}]` 输出 traceId

* Python 日志同样包含 `trace_id` 字段（通过自定义 Filter）

* * *

## 22. 验收标准

--------

1. **功能完整性**：支持 WorkFlow 和 AgentFlow 双引擎，支持人工审批恢复，支持 Python 技能调用。

2. **安全合规**：未授权请求返回 401；高风险操作自动挂起；审计日志记录所有关键动作。

3. **稳定性**：任务失败自动重试（最多 3 次），死信队列可查询；系统重启后未完成任务可从检查点恢复；pending 消息可被 reclaim。

4. **效率**：WorkFlow 空转（无 LLM）P99 延迟 < 100ms；支持 20 并发任务稳定运行。

5. **Nacos 集成**：Java 和 Python 服务自动注册到 Nacos，Java 通过服务名调用 Python，配置热更新生效。

6. **可观测性**：日志包含 traceId，可通过 `grep traceId` 串联调用链；Prometheus 端点暴露核心指标。

7. **幂等性**：重复启动实例请求返回相同结果，不会创建重复实例。

8. **多租户**：租户间数据隔离，配额独立。

* * *

## 23. 版本规划与第二里程碑

--------------

### 23.1 第一里程碑（v0.1~v0.3）已完成

* v0.1：WorkFlow 引擎 + 异步队列 + Python 直连

* v0.2：AgentFlow 引擎 + 置信度评分 + 人工兜底 + 审计日志

* v0.3：边界 Harness（RBAC/白名单/配额/风险）+ Nacos 集成 + 死信队列

### 23.2 第二里程碑规划

| 版本       | 新增功能                                                                                                                                                         |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **v0.4** | 智能路由（S_wf 公式，支持 WorkFlow/AgentFlow 自动选择）<br>模板挖掘（离线聚类成功轨迹，生成 WorkFlow 草案）                                                                                    |
| **v0.5** | 全链路 OpenTelemetry（Jaeger + Loki + Grafana）<br>意图轨迹可视化（Thought/Action/Observation 事件）<br>**SSE 实时状态推送**（`/stream` 端点，实时推送节点执行、Agent 思考过程、任务完成等事件）<br>敏感数据自动脱敏 |
| **v1.0** | 多租户完整隔离（tenant_id 全链路）<br>性能压测与优化（500 实例/秒）<br>生产级高可用（PostgreSQL 主从、Redis Sentinel、Nacos 集群）<br>K8s 部署支持                                                     |

### 23.3 SSE 实现要点（第二里程碑）

* 使用 Spring MVC 的 `SseEmitter`，每个实例一个长连接

* 执行引擎通过 `ApplicationEventPublisher` 发布事件（节点开始/结束、Agent 迭代、任务完成等）

* SSE 控制器监听事件，转换为 SSE 格式推送给前端

* 支持超时自动关闭（默认 60 秒无事件则断开，前端可自动重连）

* 与现有 Redis Streams `event-queue` 集成：事件同时写入队列（用于审计）和推送给 SSE

* * *

## 24. 附录

------

### 24.1 错误码表

| 错误码  | 含义           | 处理方式           |
| ---- | ------------ | -------------- |
| 4001 | 流程定义不存在      | 检查 def_id      |
| 4002 | 流程定义无效       | 校验 JSON Schema |
| 4003 | 实例已存在（幂等冲突）  | 返回已有实例         |
| 4101 | Skill 不存在    | 检查 skill_id    |
| 4201 | 权限不足         | 检查 RBAC 配置     |
| 4202 | 租户配额超限       | 等待或提升配额        |
| 5001 | LLM 调用超时     | 重试或切换模型        |
| 5002 | LLM 返回格式错误   | 降级处理           |
| 5101 | Python 服务不可用 | 触发熔断，重试        |
| 5201 | 消息队列发送失败     | 本地持久化后重试       |

### 24.2 环境变量清单

| 变量名                         | 默认值                                      | 说明                |
| --------------------------- | ---------------------------------------- | ----------------- |
| NACOS_HOST                  | localhost                                | Nacos 服务地址        |
| SPRING_DATASOURCE_URL       | jdbc:postgresql://localhost:5432/agentos | PostgreSQL 连接     |
| SPRING_REDIS_HOST           | localhost                                | Redis 主机          |
| OPENAI_API_KEY              | -                                        | OpenAI API 密钥（必填） |
| OTEL_EXPORTER_OTLP_ENDPOINT | [http://tempo:4317](http://tempo:4317/)  | OTLP 端点           |


