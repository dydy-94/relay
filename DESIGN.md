# ACP Relay (Java) 设计文档

> 基于 ACP（Agent Communication Protocol）自定义信封协议的轻量级 Relay 服务，使用 Java 21 + Spring Boot 3.3 + MyBatis + MySQL 实现。
> 本实现聚焦三大核心能力：**信封转发（envelope fan-out）**、**订阅管理（subscribe/unsubscribe + 历史回放）**、**Agent 管理（注册 / 心跳 / Channel 成员关系）**。

---

## 1. 项目概述

### 1.1 技术栈（来源：[pom.xml](file:///Users/cdy/opensource/relay/pom.xml)）

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-web | 3.3.5 | HTTP REST API、内嵌 Tomcat |
| spring-boot-starter-websocket | 3.3.5 | WebSocket 通信（ACP 信封协议） |
| spring-boot-starter-data-redis | 3.3.5 | Redis 跨实例 Pub/Sub 广播 + Agent Presence |
| mybatis-spring-boot-starter | 3.0.4 | MyBatis ORM 框架 |
| mysql-connector-j | 9.x | MySQL JDBC 驱动 |
| spring-boot-starter-test | 3.3.5 | JUnit 5 单元测试 |

**构建与运行环境**：Java 21，Maven，端口 `3000`，MySQL 8.x（数据库名 `relay`），Redis 7.x（`localhost:6379`，无密码）。

### 1.2 服务端点

| 协议 | 路径 | 用途 |
|------|------|------|
| WebSocket | `ws://host:3000/ws?agent_id=<id>` | ACP 信封协议主入口 |
| HTTP GET | `http://host:3000/health` | 健康检查 + 已注册 agent + 在线 agent（Redis presence） |
| HTTP POST | `http://host:3000/agent/register` | Agent 注册 |
| HTTP POST | `http://host:3000/agent/heartbeat` | Agent 心跳（REST 版本） |
| HTTP POST | `http://host:3000/api/envelope` | REST 注入信封（等价 WS publish） |
| HTTP GET | `http://host:3000/api/history` | 历史信封查询 |
| HTTP POST | `http://host:3000/api/channels` | 创建 / 更新 Channel（新频道 creator 自动成为 admin；更新已存在频道需 admin） |
| HTTP POST | `http://host:3000/api/channels/add_member` | 添加 Channel 成员（需 admin，body 携带 actor_agent_id） |
| HTTP POST | `http://host:3000/api/channels/remove_member` | 移除 Channel 成员（需 admin，body 携带 actor_agent_id） |

> **设计决策**：WebSocket 挂载于 `/ws`，agent_id 通过 query param 传入，由 `AgentIdHandshakeInterceptor` 在握手阶段提取并存入 session attributes。REST API 用于管理操作和 HTTP 信封注入（支持非 WS 客户端）。

### 1.3 配置项（来源：[application.properties](file:///Users/cdy/opensource/relay/src/main/resources/application.properties)）

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/relay?...` | MySQL 连接串 |
| `spring.datasource.username` | `root` | 数据库用户名 |
| `spring.data.redis.host` | `localhost` | Redis 主机 |
| `spring.data.redis.port` | `6379` | Redis 端口 |
| `spring.data.redis.password` | 空 | Redis 密码（本地无密码） |
| `mybatis.mapper-locations` | `classpath:mapper/*.xml` | MyBatis XML 映射文件路径 |
| `mybatis.configuration.map-underscore-to-camel-case` | `true` | snake_case → camelCase 自动映射 |
| `spring.sql.init.mode` | `always` | 启动时自动执行 schema.sql |
| `relay.ws-path` | `/ws` | WebSocket 路径 |
| `relay.replay-limit` | 200 | 历史回放最大条数 |
| `relay.history-limit` | 20 | REST 历史查询默认条数 |
| `relay.heartbeat-interval-seconds` | 5 | 心跳间隔（预留） |

---

## 2. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                    WebSocket / REST Client                    │
└────────────────────────┬────────────────────────────────────┘
                         │  WS: subscribe/publish/heartbeat
                         │  REST: envelope/history/channels
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  AcpWebSocketHandler / REST Controllers                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │subscribe │ │ publish  │ │heartbeat │ │  ping    │        │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                     │
│  │unsubscribe│ │subscribe_│ │membership│                     │
│  │           │ │membership│ │          │                     │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘                     │
└───────┼────────────┼────────────┼────────────┼──────────────┘
        │            │            │            │
        ▼            ▼            ▼            ▼
┌──────────────────────────────────────────────────────────────┐
│                     RelayState                                │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐     │
│  │ 连接管理     │  │ 信封存储+广播 │  │ Channel 管理     │     │
│  │ agentSockets │  │ storeAndBroad│  │ getOrCreate      │     │
│  │ socketAgents │  │ replay       │  │ addMember        │     │
│  └─────────────┘  └──────────────┘  │ removeMember     │     │
│                                       │ channelInfoMap    │     │
│                                       └──────────────────┘     │
│  ┌─────────────┐  ┌─────────────────────────────────────┐     │
│  │ Agent 心跳   │  │ Redis：publishToChannel/Agent       │     │
│  │ register     │  │ presence (relay:presence:*)        │     │
│  │ heartbeat    │  │ onRemoteEvent (回环保护)            │     │
│  └─────────────┘  └────────────────┬────────────────────┘     │
└──────────────────────────┬─────────┼──────────────────────────┘
                           │         │ Redis Pub/Sub (relay:events)
                           ▼         ▼
                 ┌────────────────────────┐
                 │  Redis (localhost:6379)│  ← 其他 relay 实例
                 │  relay:events topic    │
                 │  relay:presence:* keys │
                 └────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│  MyBatis Mappers                                              │
│  ┌────────────────┐  ┌────────────────┐                       │
│  │ EnvelopeMapper │  │ ChannelMapper  │                       │
│  │ (relay_envelopes│  │ (relay_channels)│                       │
│  │     表)         │  │ (relay_members) │                       │
│  │                │  │ (relay_agents)  │                       │
│  └────────┬───────┘  └────────┬───────┘                       │
└───────────┼────────────────────┼───────────────────────────────┘
            │                    │
            ▼                    ▼
         ┌──────────────────────────────┐
         │         MySQL (relay)         │
         │  relay_envelopes /            │
         │  relay_channels /             │
         │  relay_channel_members /      │
         │  relay_agents                 │
         └──────────────────────────────┘
```

---

## 3. 包结构与模块职责

| 包 | 职责 |
|----|------|
| `com.buzz.relay` | 启动入口 `RelayApplication`（`@MapperScan`） |
| `config` | 配置属性绑定 `RelayProperties` + Redis Pub/Sub 容器 `RedisConfig` |
| `model` | ACP 信封模型 `ChatEnvelope` / `ChannelInfo` / `AgentHeartbeat` |
| `store` | MyBatis Mapper 接口 + Row POJO（`EnvelopeMapper` / `ChannelMapper` / `EnvelopeRow` / `ChannelRow` / `ChannelMemberRow`） |
| `relay` | 全局状态管理器 `RelayState`（连接索引 + 信封存储广播 + Channel 管理 + 心跳 + 跨实例广播 + Presence）+ Redis 订阅者 `RedisEventSubscriber` |
| `ws` | WebSocket 处理器 `AcpWebSocketHandler` + 配置 `WebSocketConfig` + 握手拦截器 `AgentIdHandshakeInterceptor` |
| `api` | REST 控制器：`AgentController` / `EnvelopeController` / `ChannelController` |

---

## 4. 核心模块详细设计

### 4.1 信封模型 — [ChatEnvelope.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/model/ChatEnvelope.java)

ACP 自定义信封格式，字段分三组：

**路由元数据**：
| 字段 | 类型 | 说明 |
|------|------|------|
| `envelopeId` | String (UUID) | 信封唯一标识 |
| `channelId` | String | 所属 channel |
| `projectId` | String | 项目 ID（可选） |
| `topicId` | String | 话题 ID（可选） |
| `parentEnvelopeId` | String | 父信封 ID（对话链） |
| `rootEnvelopeId` | String | 根信封 ID（thread root） |
| `mentions` | List\<String\> | 提及的 agent_id 列表 |

**发送者**：
| 字段 | 类型 | 说明 |
|------|------|------|
| `senderId` | String | 发送者标识 |
| `senderRole` | String | 角色（`expert` / `human` / ...） |
| `createdAtMs` | long | 创建时间戳 |

**Payload（四选一）**：
| 字段 | 类型 | kind 值 | 说明 |
|------|------|---------|------|
| `chat` | Map\<String, Object\> | `chat` | 聊天消息（content 等） |
| `taskAssign` | Map\<String, Object\> | `task_assign` | 任务分配 |
| `taskResult` | Map\<String, Object\> | `task_result` | 任务结果 |
| `command` | Map\<String, Object\> | `command` | 命令 |

**辅助字段**：`traceId`（链路追踪）、`metadata`（自定义元数据）。

> **序列化策略**：Jackson 使用 `PropertyNamingStrategies.SNAKE_CASE`，Java 侧 camelCase 字段自动映射到 JSON 的 snake_case（`envelopeId` ↔ `envelope_id`）。

**工厂方法**：
```java
ChatEnvelope.newEnvelope(kind, senderId, senderRole, channelId)
```

### 4.2 Channel 元信息 — [ChannelInfo.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/model/ChannelInfo.java)

| 字段 | 说明 |
|------|------|
| `channelId` | channel 唯一标识 |
| `name` | 人类可读名称 |
| `channelType` | `stream` / `forum` / `dm` / `private` / `workflow` |
| `visibility` | `open` / `private` |
| `members` | 成员列表（pubkey + name + role） |
| `archived` | 是否已归档 |
| `isDm` | 是否 DM |

### 4.3 MySQL Schema — [schema.sql](file:///Users/cdy/opensource/relay/src/main/resources/schema.sql)

4 张表，启动时自动创建：

#### relay_envelopes 表
| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGINT AUTO_INCREMENT | 主键 |
| `envelope_id` | VARCHAR(64) | UUID 信封 ID（UNIQUE） |
| `channel_id` | VARCHAR(128) | 所属 channel |
| `parent_envelope_id` | VARCHAR(64) | 父信封 ID |
| `root_envelope_id` | VARCHAR(64) | 根信封 ID |
| `sender_id` | VARCHAR(128) | 发送者 |
| `sender_role` | VARCHAR(32) | 发送者角色 |
| `kind` | VARCHAR(32) | chat / task_assign / task_result / command |
| `mentions` | JSON | 提及列表 |
| `payload` | JSON | 业务载荷（四选一） |
| `metadata` | JSON | 自定义元数据 |
| `created_at_ms` | BIGINT | 创建时间 |
| `received_at_ms` | BIGINT | relay 收到时间 |

#### relay_channels 表
| 列 | 类型 | 说明 |
|----|------|------|
| `channel_id` | VARCHAR(128) | channel 唯一标识（UNIQUE） |
| `name` | VARCHAR(256) | 名称 |
| `channel_type` | VARCHAR(32) | 类型 |
| `visibility` | VARCHAR(32) | 可见性 |
| `description` | TEXT | 描述 |
| `archived` | BOOLEAN | 是否归档 |

#### relay_channel_members 表
| 列 | 类型 | 说明 |
|----|------|------|
| `channel_id` + `agent_id` | — | UNIQUE 约束防重复 |
| `role` | VARCHAR(32) | 成员角色 |
| `name` | VARCHAR(256) | 成员显示名 |

#### relay_agents 表
| 列 | 类型 | 说明 |
|----|------|------|
| `agent_id` | VARCHAR(128) | agent 唯一标识（UNIQUE） |
| `sandbox_id` | VARCHAR(128) | 沙箱 ID |
| `capabilities` | JSON | 能力声明 |
| `rules` | JSON | 规则配置 |
| `status` | VARCHAR(32) | idle / busy |
| `active_tasks` | INT | 活跃任务数 |
| `last_heartbeat_ms` | BIGINT | 最后心跳时间 |

### 4.4 MyBatis Mapper

#### EnvelopeMapper — [EnvelopeMapper.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/store/EnvelopeMapper.java) + [EnvelopeMapper.xml](file:///Users/cdy/opensource/relay/src/main/resources/mapper/EnvelopeMapper.xml)

| 方法 | SQL | 说明 |
|------|-----|------|
| `insert` | INSERT INTO relay_envelopes ... | 插入信封 |
| `findById` | SELECT ... WHERE envelope_id = ? | 按 ID 查找 |
| `findHistory` | SELECT ... WHERE channel_id = ? [AND root_envelope_id = ?] ORDER BY created_at_ms ASC LIMIT ? | 历史查询 |
| `findReplay` | SELECT ... WHERE channel_id = ? AND created_at_ms > ? ORDER BY created_at_ms ASC LIMIT ? | 历史回放 |

#### ChannelMapper — [ChannelMapper.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/store/ChannelMapper.java) + [ChannelMapper.xml](file:///Users/cdy/opensource/relay/src/main/resources/mapper/ChannelMapper.xml)

| 方法 | 说明 |
|------|------|
| `insertChannel` | 插入 channel |
| `findChannelById` | 按 channel_id 查找 |
| `updateChannel` | 更新 channel 元信息 |
| `findChannelsByAgent` | JOIN relay_channel_members，查 agent 加入的所有 channel |
| `insertMember` | `INSERT IGNORE` 添加成员（幂等） |
| `deleteMember` | 移除成员 |
| `findMembers` | 查 channel 所有成员 |
| `findMember` | 检查成员关系 |
| `insertAgent` | `INSERT IGNORE` 注册 agent（幂等） |
| `updateHeartbeat` | 更新心跳状态 |
| `findAllAgents` | 查所有已注册 agent |

### 4.5 全局状态管理器 — [RelayState.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/relay/RelayState.java)

`@Component` 单例，核心职责：

#### 4.5.1 连接管理

```java
// agent_id → WebSocket sessions（一个 agent 可多 WS 连接）
Map<String, Set<WebSocketSession>> agentSockets;

// session → agent_id（反向索引，断线时清理）
Map<String, String> socketAgents;
```

- `enrollSocket(agentId, session)` — 注册连接 + 刷新 Redis presence
- `unenrollSocket(session)` — 注销连接（自动清理空 set；本实例无该 agent 连接时清理 presence）
- `broadcastToChannel(channelId, msg)` — 查 relay_channel_members → 遍历成员的 WS sessions → 发送（仅本实例）
- `broadcastToAgent(agentId, msg)` — 给某个 agent 的所有连接发消息（仅本实例）
- `publishToChannel(channelId, msg)` / `publishToAgent(agentId, msg)` — 本实例推送 + 发布到 Redis（跨实例）
- `sendToSession(session, msg)` — 定向发送

#### 4.5.2 信封存储 + 广播

**`storeAndBroadcast(channelId, envelope)`**：
1. 补全 `envelopeId`（UUID）和 `createdAtMs`
2. `EnvelopeRow` 序列化（mentions / payload / metadata → JSON）
3. `envelopeMapper.insert` 存入 MySQL（envelope_id 重复时静默忽略）
4. 构建广播 Map（snake_case）
5. `publishToChannel` 推给本实例在线成员 + 发布到 Redis 供其他实例推送

**Payload 序列化策略**：根据 `kind` 字段选择对应 payload 存入 `payload` JSON 列：
```
chat → env.getChat()
task_assign → env.getTaskAssign()
task_result → env.getTaskResult()
command → env.getCommand()
```

#### 4.5.3 历史回放

**`replayEnvelopes(channelId, sinceMs, limit)`**：
- 调用 `envelopeMapper.findReplay`，返回 `created_at_ms > sinceMs` 的信封
- 按时间升序，JSON → Map 反序列化还原

**`getHistory(channelId, rootEnvelopeId, limit)`**：
- 调用 `envelopeMapper.findHistory`，可选按 `root_envelope_id` 过滤
- REST `/api/history` 调用

#### 4.5.4 Channel 管理

- `getOrCreateChannel(channelId)` — 幂等创建
- `updateChannelInfo(channelId, ...)` — 更新元信息
- `addMember(channelId, agentId, name, role)` — `INSERT IGNORE` + 广播 `membership(joined)` + 推 `channel_info` 给新成员；**频道首个成员固定为 admin**（见 [4.9](#49-channel-权限模型)）
- `removeMember(channelId, agentId)` — 删除 + 广播 `membership(left)`
- `channelInfoMap(ch)` — 构建 `channel_info` 消息帧（含成员列表）

#### 4.5.5 Redis 跨实例广播 + Presence

**跨实例广播**（`RedisConfig` 定义 topic `relay:events`，`RedisEventSubscriber` 订阅）：

```
publishToChannel / publishToAgent
        │
        ├─ 本实例：broadcastToChannel / broadcastToAgent（内存索引直推）
        └─ 其他实例：redis.convertAndSend("relay:events", {
               "instance_id": "<本实例 UUID>",
               "target": "channel" | "agent",
               "channel_id" / "agent_id": ...,
               "payload": { ...消息帧... }
           })
              ↓ Redis Pub/Sub
        其他实例 RedisEventSubscriber → state.onRemoteEvent(json)
              ├─ instance_id == 本实例 → 跳过（回环保护）
              └─ 否则 → broadcastToChannel / broadcastToAgent（只推自己内存中的在线连接）
```

- 每个 JVM 启动时生成唯一 `instanceId`（`UUID.randomUUID()`），发布时携带，订阅端据此跳过自己发布的消息，避免重复推送。
- 各实例共享同一 MySQL，channel 成员表全局可见；实例收到远程事件后查成员表，只推本实例的在线连接。

**Presence（Agent 在线状态）**：

- Key 格式：`relay:presence:{agent_id}` → 值 = 最后一次注册的 `instance_id`，TTL 60s。
- `enrollSocket` / 收到任何 WS 消息（`touchPresence`）时覆盖写并续期 TTL。
- `unenrollSocket`：本实例已无该 agent 连接时，仅当 Redis 值归属本实例才删除（避免误删其他实例上的连接）。
- `getOnlineAgents()`：扫描 `relay:presence:*`，聚合所有实例的在线 agent，供 `/health` 返回 `online_agents`。

> **设计要点**：presence 用 TTL 兜底 —— 即使进程崩溃未清理，60s 后 key 自动过期，不会出现永久"幽灵在线"。

### 4.6 WebSocket 处理器 — [AcpWebSocketHandler.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/ws/AcpWebSocketHandler.java)

#### 协议帧格式

**Client → Server**：

| 命令 | 结构 | 说明 |
|------|------|------|
| `subscribe` | `{"type":"subscribe", "channel_id":"...", "since_ms":0, "limit":200}` | 订阅 channel |
| `unsubscribe` | `{"type":"unsubscribe", "channel_id":"..."}` | 取消订阅 |
| `subscribe_membership` | `{"type":"subscribe_membership", "since_ms":0}` | 订阅成员关系变化 |
| `publish` | `{"type":"publish", "envelope":{...}}` | 发布信封 |
| `heartbeat` | `{"type":"heartbeat", "agent_id":"...", "status":"idle", "active_tasks":0}` | 心跳 |
| `ping` | `{"type":"ping"}` | 心跳探测 |

**Server → Client**：

| 类型 | 说明 |
|------|------|
| `envelope` | 信封推送（实时 fan-out 或历史回放） |
| `membership` | 成员变更事件（joined / left） |
| `channel_info` | Channel 元信息（含成员列表） |
| `eose` | End of stored events（历史回放结束） |
| `pong` | 心跳回复（带 echo） |
| `error` | 错误信息 |

#### 命令处理

**`subscribe`**：
```
1. canRead 校验 → private 频道非成员返回 FORBIDDEN（open 频道任何人可订阅）
2. 确保 channel 存在：open 频道自动加入成员（addMember 幂等）；private 频道不自动加入
3. replayEnvelopes → 回放历史信封（created_at_ms > since_ms）
4. channelInfoMap → 推 channel_info
5. eose → 推结束标记
6. 记录订阅状态
```

**`publish`**：
```
1. 解析 envelope JSON → ChatEnvelope（snake_case → camelCase）
2. 补全 sender_id（如缺失，用连接的 agent_id）
3. canWrite 校验 → 非成员返回 FORBIDDEN
4. storeAndBroadcast → 存 MySQL + fan-out
5. 回复 pong(echo=envelope_id)
```

**`heartbeat`**：
```
1. updateHeartbeat(agentId, status, activeTasks) → upsert 到 relay_agents 表
2. 回复 pong(echo="heartbeat")
```

**`subscribe_membership`**：
```
1. 查 agent 已加入的所有 channel
2. 逐个推 channel_info
3. 推 eose(channel_id="__membership__")
```

### 4.7 握手拦截器 — [AgentIdHandshakeInterceptor.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/ws/AgentIdHandshakeInterceptor.java)

从 WS 握手 URL 的 query param 中提取 `agent_id`，存入 session attributes：
```
ws://localhost:3000/ws?agent_id=my-agent-001
                               ↓
session.getAttributes().put("agentId", "my-agent-001")
```

> **设计原因**：Spring WebSocket 的 `WebSocketSession.getUri()` 在某些实现中不暴露 query string，通过 HandshakeInterceptor 在握手阶段捕获并存入 attributes 是最可靠的方式。

### 4.8 REST 控制器

#### [AgentController.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/api/AgentController.java)

| 端点 | 方法 | 说明 |
|------|------|------|
| `GET /health` | health() | 返回 `{ok, instance_id, agents, online_agents}` — agents 为 MySQL 已注册 agent，online_agents 为 Redis presence 聚合 |
| `POST /agent/register` | register() | 注册 agent（capabilities/rules 序列化为 JSON） |
| `POST /agent/heartbeat` | heartbeat() | 更新心跳状态 |

#### [EnvelopeController.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/api/EnvelopeController.java)

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /api/envelope` | inject() | REST 注入信封 → 存储 + 广播 |
| `GET /api/history` | history() | 查询历史信封（channel_id + 可选 root_envelope_id + limit） |

#### [ChannelController.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/api/ChannelController.java)

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /api/channels` | createChannel() | 创建 / 更新 channel（新频道 creator 自动成为首个 admin；更新已存在频道需 admin） |
| `POST /api/channels/add_member` | addMember() | 添加成员（需 admin，body 携带 actor_agent_id 标识操作者） |
| `POST /api/channels/remove_member` | removeMember() | 移除成员（需 admin） |

> 管理操作失败时返回 `403 FORBIDDEN`；参数缺失返回 `400 BAD_REQUEST`。

### 4.9 Channel 权限模型

基于 `visibility`（open / private）+ 成员 `role`（admin / member）的准入与分级授权模型。

#### 角色与可见性

| 概念 | 取值 | 说明 |
|------|------|------|
| `visibility` | `open` / `private` | 决定准入方式：open 任何人可订阅加入；private 仅被添加的成员可访问 |
| `role` | `admin` / `member` | 决定操作授权：管理操作仅 admin；读写操作对成员开放 |

#### 首个成员固定为 admin

`addMember` 中新增逻辑：向一个**尚无任何成员**的频道添加首个成员时，其 role 强制为 `admin`。

```java
boolean firstMember = channelMapper.findMembers(channelId).isEmpty();
if (firstMember) {
    role = "admin"; // 首个成员固定 admin，避免"无主频道"无法被管理
}
```

由此：
- **open 频道**：第一个人通过 WS subscribe 自动加入时即成为 admin，之后可管理该频道。
- **private 频道**：由 REST 创建（`creator_agent_id` 成为 admin），再由 admin 通过 `add_member` 拉入其他成员。

#### 读写 / 管理权限矩阵

| 操作 | open 频道 | private 频道 | 校验位置 |
|------|-----------|--------------|----------|
| 订阅（读） | 任何人（自动加入成员） | 仅成员（`canRead`） | [AcpWebSocketHandler.handleSubscribe](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/ws/AcpWebSocketHandler.java) |
| 发布（写） | 仅成员 | 仅成员（`canWrite`） | [AcpWebSocketHandler.handlePublish](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/ws/AcpWebSocketHandler.java) |
| 添加 / 移除成员 | 仅 admin | 仅 admin | [ChannelController](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/api/ChannelController.java) |
| 更新频道元信息 | 仅 admin | 仅 admin | ChannelController |

> **要点**：open 频道**订阅即加入**（WS subscribe 自动 `addMember`），因此后续 publish 也有写权限；private 频道订阅不会自动加入，非成员订阅返回 `FORBIDDEN`。

#### 操作者身份：`actor_agent_id`

REST 管理接口无独立认证（可信内部网络），操作者身份通过请求 body 中的 `actor_agent_id` 声明：

```json
{
  "channel_id": "ch-1",
  "agent_id": "agent-b",
  "actor_agent_id": "agent-a"   // 声明操作者，服务端校验其是否 admin
}
```

权限判断统一走 [RelayState](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/relay/RelayState.java) 的 `isMember` / `isAdmin` / `canRead` / `canWrite`，底层均查询 `relay_channel_members` 表（与 WS 侧同一数据源，保证一致性）。

#### 设计取舍

- **open 频道"订阅即加入"**：与"open 任何人可读"语义一致，且使公开频道无需预配置成员即可发布，贴近群聊/广播场景。
- **首个成员 admin**：避免频道创建后无人具备管理权限的死锁；创建者（REST `creator_agent_id`）即为自然的首个 admin。
- **不存在的频道按 open 处理**：`canRead` 对不存在的频道返回 true，保证 `subscribe` 首次接触即创建并加入（与 `getOrCreateChannel` 幂等创建对齐）。

---

## 5. 消息流转时序

### 5.1 订阅 + 历史回放

```
Client                              Relay
  │                                   │
  │── ws://host:3000/ws?agent_id=A ──►│
  │                                   │ AgentIdHandshakeInterceptor 提取 agent_id
  │                                   │ enrollSocket(agentId, session)
  │                                   │
  │──── subscribe(channel_id, since_ms)─►│
  │                                   │ canRead 校验（private 非成员 → FORBIDDEN）
  │                                   │ getOrCreateChannel → addMember(agentA)
  │                                   │   （open 频道自动加入；首个成员为 admin）
  │                                   │   → broadcast membership(joined) to channel
  │◄── channel_info ─────────────────│
  │◄── envelope (历史1) ─────────────│  replayEnvelopes(since_ms)
  │◄── envelope (历史2) ─────────────│
  │◄── eose ─────────────────────────│
```

### 5.2 信封发布 + 实时 fan-out

```
Client A                            Relay                          Client B
  │                                   │                               │
  │──── publish(envelope) ───────────►│                               │
  │                                   │ storeAndBroadcast()           │
  │                                   │   → envelopeMapper.insert()   │
  │                                   │   → publishToChannel()        │
  │                                   │     ├─ 本实例直推 ────────────►│
  │                                   │     └─ Redis Pub/Sub ──► 其他实例 → 推其在线连接
  │◄── pong(echo=envelope_id) ────────│                               │
```

### 5.3 REST 注入 + WS 推送

```
REST Client                    Relay                          WS Client (已订阅)
  │                              │                               │
  │── POST /api/envelope ───────►│                               │
  │                              │ storeAndBroadcast()            │
  │◄── {ok, envelope_id} ────────│                               │
  │                              │──── envelope (WS push) ──────►│
```

### 5.4 成员变更通知

```
REST Client                    Relay                          WS Client (成员)
  │                              │                               │
  │── POST /api/channels/       │                               │
  │   add_member ──────────────►│                               │
  │                              │ isAdmin(actor) 校验 → 403     │
  │                              │ insertMember (INSERT IGNORE) │
  │                              │ broadcast membership(joined) │
  │                              │──── membership(joined) ──────►│
  │                              │──── channel_info ────────────►│ (给新成员)
  │◄── {ok} ────────────────────│                               │
```

---

## 6. Agent 管理

### 6.1 Agent 注册

**REST**：`POST /agent/register`
```json
{
  "agent_id": "my-agent",
  "sandbox_id": "sb-1",
  "capabilities": {"tags": ["code", "review"]},
  "rules": [{"all_channels": true, "kinds": ["chat"]}]
}
```
- `capabilities` 和 `rules` 通过 Jackson 序列化为 JSON 字符串存入 `relay_agents` 表
- `INSERT IGNORE` 语义：重复注册不会报错

### 6.2 Agent 心跳

**WS**：`{"type":"heartbeat", "agent_id":"...", "status":"idle", "active_tasks":0}`
**REST**：`POST /agent/heartbeat`

- 先 `INSERT IGNORE` 确保 agent 存在（WS 连接的 agent 可能未通过 REST 注册）
- 再 `UPDATE` 状态、活跃任务数、心跳时间
- `GET /health` 返回所有已注册 agent 的状态

### 6.3 Channel 成员关系

| 操作 | 触发方式 | 权限约束 | 广播事件 |
|------|----------|----------|----------|
| 加入 channel | WS subscribe / REST add_member | open：订阅即加入；private：仅 admin 添加；首个成员固定为 admin | `membership(joined)` + `channel_info` |
| 离开 channel | REST remove_member | 需 admin（`actor_agent_id`） | `membership(left)` |
| 查询成员 | REST / WS channel_info | private 频道仅成员可见 | `channel_info`（含成员列表） |
| 查询 agent 的 channel | subscribe_membership | 仅本人 | 遍历推 `channel_info` |

> 权限规则详见 [4.9 Channel 权限模型](#49-channel-权限模型)。

---

## 7. 关键设计决策

| 决策 | 原因 |
|------|------|
| 自定义信封协议，非 Nostr | ACP 是内部可信协议，不需要 BIP340 签名验证；信封格式更贴合 agent 通信需求（payload 四选一、mentions、trace_id） |
| MyBatis + MySQL | 用户要求 MyBatis ORM；MySQL 提供持久化存储，重启不丢失历史信封 |
| Jackson snake_case | ACP 协议 JSON 使用 snake_case（`envelope_id`），Java 使用 camelCase（`envelopeId`），通过 `PropertyNamingStrategies.SNAKE_CASE` 自动映射 |
| HandshakeInterceptor | Spring WebSocketSession 不稳定暴露 URI query，握手阶段提取 agent_id 最可靠 |
| 心跳 upsert | WS 连接的 agent 可能未通过 REST 注册，`updateHeartbeat` 先 `INSERT IGNORE` 再 `UPDATE` |
| Redis Pub/Sub 跨实例广播 | 多实例部署时，信封 / membership 事件通过 `relay:events` topic 分发；instance_id 回环保护避免重复推送 |
| Redis Presence | agent 在线状态存 `relay:presence:{agent_id}`（TTL 60s 兜底），跨实例聚合；断开时仅归属本实例才清理 |
| open/private 可见性 + admin/member 角色 | private 频道仅成员可订阅 / 发布；管理操作（增删成员 / 更新元信息）仅 admin；首个成员固定 admin，避免"无主频道"无法被管理 |
| `actor_agent_id` 操作者声明 | REST 无独立认证（可信内部网络），管理操作通过 body 的 `actor_agent_id` 声明操作者并校验 admin，与 WS agent_id 同一信任模型 |
| `INSERT IGNORE` 语义 | channel 创建、成员加入、agent 注册都是幂等操作，重复调用不报错 |
| tags JSON 列 | mentions / payload / metadata 用 JSON 列存储，MyBatis 处理为 String，Java 层反序列化 |
| 无签名验证 | ACP relay 是可信内部服务，agent_id 由连接层标识，不需要密码学认证 |

---

## 8. 测试

### E2E 测试 — [acp_relay_e2e_test.py](file:///tmp/acp_relay_e2e_test.py)

Python + websocket-client + requests，25 项测试覆盖完整协议流程：

| # | 测试 | 验证点 |
|---|------|--------|
| 1 | REST 创建 channel + add_member | channel_info 返回正确 |
| 2 | WS subscribe + eose | 收到 channel_info + eose |
| 3 | REST 注入信封 → WS 实时推送 | WS 收到 envelope fan-out |
| 4 | WS publish → 跨连接 fan-out | publisher 收到 pong + subscriber 收到 envelope |
| 5 | 历史回放 | 新连接 subscribe since_ms=0 收到历史信封 |
| 6 | heartbeat + /health | pong 回复 + agent 在 /health 可见 |
| 7 | ping/pong | 基础心跳探测 |
| 8 | subscribe_membership | 收到已加入 channel 的 channel_info + eose |
| 9 | REST /api/history | 返回历史信封列表 |
| 10 | agent register | REST 注册 agent 成功 |
| 11 | Channel 权限（private + admin） | 创建者 admin；非成员订阅 private → FORBIDDEN；非 admin add/remove_member → 403；admin 操作 → 200；非成员 publish → FORBIDDEN；admin 更新频道 → 200 / 非 admin → 403 |

```
=== Results: 25 passed, 0 failed ===
ALL TESTS PASSED
```

---

## 9. 运行方式

```bash
cd /Users/cdy/opensource/relay

# 构建
mvn -DskipTests package

# 启动（自动建表 + 连接 MySQL）
java -jar target/buzz-relay-0.1.0.jar

# WebSocket: ws://localhost:3000/ws?agent_id=<your-agent-id>
# REST:      http://localhost:3000/health
```

**MySQL 前置条件**：
- 数据库 `relay` 已创建
- 用户 `root` 密码 `1994cheche` 可访问
- `schema.sql` 在启动时自动执行（`spring.sql.init.mode=always`）

---

## 10. 已知限制与扩展方向

| 项 | 现状 | 扩展方向 |
|----|------|----------|
| 认证 | agent_id query param（可信内部网络） | API Key / mTLS |
| 持久化 | MySQL 单实例 | 读写分离 / 分库分表 |
| 分布式 | 多实例 + Redis Pub/Sub 广播 + Presence 已支持 | 见 [11. 分布式部署方案](#11-分布式部署方案)：无状态化（session/订阅归一到 Redis）、消息幂等去重、Redis AOF 持久化 |
| 消息大小 | 无限制 | 帧大小限制 + 大消息拒绝 |
| 限流 | 无 | 按连接 / IP 速率限制 |
| NIP-50 搜索 | 无 | 全文索引（MATCH AGAINST 或 Elasticsearch） |
| WS 断线重连 | 客户端实现 | 服务端 session 恢复 |
| Channel 权限 | 已支持 open/private 准入 + admin/member 角色（见 [4.9](#49-channel-权限模型)） | 细粒度 RBAC（按 kind 限制、Mention-only、子频道权限继承） |

---

## 11. 分布式部署方案

> 本项目**从设计之初即为多实例分布式部署**，当前实现已覆盖核心分布式能力。本节说明现状、Redis 依赖边界、故障降级行为与后续无状态化改造路径。

### 11.1 部署拓扑

```
  Agent WS ───► Relay-A ──┐
  Agent WS ───► Relay-B ──┼──► MySQL（共享，唯一持久化源）
  Agent WS ───► Relay-C ──┘
                      │
                      └──► Redis（Pub/Sub 广播 + Presence，易失）
```

- 每个实例是独立的 Spring Boot 进程，端口可各自配置（默认 3000），实例间**无直连**，仅通过共享的 MySQL + Redis 协作。
- 前端通过负载均衡（LB / DNS）把不同 agent 的 WS 连接分发到不同实例。
- 各实例 `instanceId` 唯一（JVM 启动生成 UUID），用于 Pub/Sub 回环保护与 presence 归属判断。

### 11.2 依赖边界：不是"完全依赖 Redis"

**MySQL 是唯一持久化源（source of truth）**，Redis 只承载两类**易失**状态：

| 状态 | 存储 | 是否持久化 | 丢失影响 |
|------|------|-----------|----------|
| 信封（历史消息） | MySQL `relay_envelopes` | ✅ 持久 | 无（唯一权威副本） |
| Channel / 成员 / 角色 | MySQL `relay_channels` / `relay_channel_members` | ✅ 持久 | 无 |
| Agent 注册 / 心跳 | MySQL `relay_agents` | ✅ 持久 | 无 |
| 跨实例实时广播 | Redis Pub/Sub（瞬时） | ❌ 易失 | 其他实例延迟/收不到，本实例内存直推不受影响 |
| Agent 在线状态（presence） | Redis `relay:presence:*`（TTL 60s） | ❌ 易失 | `/health` 在线列表为空，60s 自动过期 |

因此**消息的最终可靠性由 MySQL 保证，不依赖 Redis**。Redis 承载的是"实时性"和"全局可见性"，而非"不丢数据"。

### 11.3 Redis 故障时的降级行为

所有 Redis 调用在 [RelayState.java](file:///Users/cdy/opensource/relay/src/main/java/com/buzz/relay/relay/RelayState.java) 中均已 `try/catch` 包裹，Redis 完全不可达时：

| 功能 | 降级表现 |
|------|----------|
| 本实例实时广播 | ✅ 不受影响（内存 `agentSockets` 直推） |
| 跨实例广播 | ⚠️ `publishRemote` 仅 `log.warn`；其他实例收不到，但消息已写入 MySQL，可通过历史回放补齐 |
| Presence 读写 | ⚠️ 读写失败被忽略，`/health` 的 `online_agents` 为空，不影响消息收发 |
| 权限判断 | ✅ 不受影响（基于 MySQL 成员表） |
| 信封 / 历史 / 回放 | ✅ 不受影响（MySQL） |

> **结论**：Redis 故障不会丢消息、不会破坏权限，只损失跨实例实时推送和在线状态聚合；同一实例上的连接仍能正常实时通信。

### 11.4 Redis 自身持久化建议

由于 Redis 只承载易失状态，即使 Redis 重启丢失 presence / 广播，消息可靠性也不受影响（权威副本在 MySQL）。但为减少故障窗口仍建议：

- 开启 **AOF**（`appendfsync everysec`）——广播是瞬时 Pub/Sub，AOF 主要保护的是如果未来把订阅关系/队列放入 Redis 时的数据。
- 生产环境使用 **Redis 主从 + Sentinel** 或云托管（如 ElastiCache）保证可用性；AOF/RDB 兜底重启恢复。

### 11.5 后续无状态化改造路径

当前每个实例维护本地 session 索引（`agentSockets` / `socketAgents`），这是**实例本地状态**。两种演进方向：

| 方案 | 做法 | 取舍 |
|------|------|------|
| **A. 连接粘滞（推荐先行）** | 网关按 agent_id 哈希把同一 agent 的 WS 连接固定路由到同一实例 | 改动极小，现有内存索引即全局正确；实例重启丢该 agent 连接需客户端重连 |
| **B. 订阅关系入 Redis** | 把 session→channel 订阅表迁到 Redis，实例间可接管连接 | 实现无状态化、支持连接迁移；需处理订阅去重、消息幂等、session 生命周期，工作量大 |

### 11.6 改造工作量评估

- **多实例水平扩展（生产部署）**：**当前已就绪**。部署 N 份 jar + LB 分发即可，无需改代码——跨实例广播、presence、权限（MySQL 层全局一致）均已实现。
- **方案 A（连接粘滞）**：小改动（仅网关路由配置），是"实例重启不丢订阅关系"的最低成本方案。
- **方案 B（完全无状态化）**：中到大的改动，涉及订阅表迁移 Redis、消息幂等去重、session 生命周期管理，收益是"任一实例故障可无缝接管连接"。
- **Redis 持久化 / 高可用**：运维侧配置（AOF / 主从 / 云托管），不涉及业务代码。

> 建议路线：**先按 11.6 第一行直接部署多实例 + 方案 A 粘滞**，满足绝大多数生产场景；只有当需要"实例故障零感知迁移连接"时才投入方案 B。
