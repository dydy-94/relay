-- ACP Relay — MySQL schema
-- 自定义信封协议（非 Nostr），所有表统一加 relay_ 前缀：
-- relay_envelopes / relay_channels / relay_channel_members / relay_agents

-- 信封存储
CREATE TABLE IF NOT EXISTS relay_envelopes (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    envelope_id         VARCHAR(64)   NOT NULL COMMENT 'UUID 信封 ID',
    channel_id          VARCHAR(128)  NOT NULL COMMENT '所属 channel',
    project_id          VARCHAR(128)  NULL,
    topic_id            VARCHAR(128)  NULL,
    parent_envelope_id  VARCHAR(64)   NULL     COMMENT '父信封 ID（对话链）',
    root_envelope_id    VARCHAR(64)   NULL     COMMENT '根信封 ID（thread root）',
    sender_id           VARCHAR(128)  NOT NULL,
    sender_role         VARCHAR(32)   NOT NULL DEFAULT 'expert',
    kind                VARCHAR(32)   NOT NULL DEFAULT 'chat' COMMENT 'chat/task_assign/task_result/command',
    mentions            JSON          NULL     COMMENT '提及的 agent_id 列表',
    payload             JSON          NULL     COMMENT '业务载荷（chat/task_assign/task_result/command 四选一）',
    metadata            JSON          NULL,
    trace_id            VARCHAR(64)   NULL,
    created_at_ms       BIGINT        NOT NULL,
    received_at_ms      BIGINT        NOT NULL COMMENT 'relay 收到时间',

    UNIQUE INDEX uk_envelope_id (envelope_id),
    INDEX idx_channel (channel_id),
    INDEX idx_root (root_envelope_id),
    INDEX idx_sender (sender_id),
    INDEX idx_created (created_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Channel 元信息
CREATE TABLE IF NOT EXISTS relay_channels (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id      VARCHAR(128)  NOT NULL COMMENT 'channel 唯一标识',
    name            VARCHAR(256)  NULL,
    channel_type    VARCHAR(32)   NOT NULL DEFAULT 'stream' COMMENT 'stream/forum/dm/private/workflow',
    visibility      VARCHAR(32)   NOT NULL DEFAULT 'open',
    description     TEXT          NULL,
    archived        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at_ms   BIGINT        NOT NULL,

    UNIQUE INDEX uk_channel_id (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Channel 成员关系
CREATE TABLE IF NOT EXISTS relay_channel_members (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id      VARCHAR(128)  NOT NULL,
    agent_id        VARCHAR(128)  NOT NULL,
    role            VARCHAR(32)   NOT NULL DEFAULT 'member',
    name            VARCHAR(256)  NULL,
    added_at_ms     BIGINT        NOT NULL,

    UNIQUE INDEX uk_channel_agent (channel_id, agent_id),
    INDEX idx_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agent 注册信息
CREATE TABLE IF NOT EXISTS relay_agents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id            VARCHAR(128)  NOT NULL,
    sandbox_id          VARCHAR(128)  NULL,
    capabilities        JSON          NULL,
    rules               JSON          NULL,
    registered_at_ms    BIGINT        NOT NULL,
    last_heartbeat_ms  BIGINT        NULL,
    status              VARCHAR(32)   NULL DEFAULT 'idle',
    active_tasks        INT           NOT NULL DEFAULT 0,

    UNIQUE INDEX uk_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dashboard 超管账号（人，非 agent）：账号密码登录后管理 relay
CREATE TABLE IF NOT EXISTS relay_super_admins (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(128)  NOT NULL COMMENT '登录账号',
    password_hash    VARCHAR(256)  NOT NULL COMMENT 'BCrypt 哈希',
    display_name     VARCHAR(256)  NULL COMMENT '显示名',
    enabled          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at_ms    BIGINT        NOT NULL,
    last_login_at_ms BIGINT        NULL,

    UNIQUE INDEX uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
