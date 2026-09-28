-- 第 3 掌冻结的三张最小表。
-- 表结构一旦被后续章节依赖，改动成本会指数上升，所以先冻结表、再冻结接口。
-- 后续各掌新增字段一律用新的迁移脚本，不修改本文件。

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- projectId 是后续记忆隔离、知识库隔离、Agent 隔离、会话隔离的公共锚点
CREATE TABLE digital_human_project (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id       BIGINT       NOT NULL,
    name           VARCHAR(128) NOT NULL,
    title          VARCHAR(128) NOT NULL,
    theme_color    VARCHAR(16)  NOT NULL DEFAULT '#2F6BFF',
    background_url VARCHAR(512) DEFAULT NULL,
    opening_line   VARCHAR(512) DEFAULT NULL,
    closing_line   VARCHAR(512) DEFAULT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_digital_human_project_owner (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 本掌刻意把 Agent 配置做成项目的一个属性（一对一），第 9 掌引入 ReactAgent 时只扩展来源，不动主键关系
CREATE TABLE agent_config (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    project_id    BIGINT        NOT NULL,
    model         VARCHAR(64)   NOT NULL DEFAULT 'deepseek-flash',
    system_prompt TEXT          NOT NULL,
    temperature   DECIMAL(3, 2) NOT NULL DEFAULT 0.70,
    max_tokens    INT           NOT NULL DEFAULT 1024,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_config_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
