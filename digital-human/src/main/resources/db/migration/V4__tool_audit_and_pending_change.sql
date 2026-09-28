-- 第 6 掌：工具调用要「敢用」，得补两样东西——审计与确认。
--   tool_call_audit：每次工具调用留痕（谁、哪个工具、什么参数、结果、耗时、追踪号）
--   pending_title_change：写工具在人类确认之前不落业务数据，只落一条待确认记录

CREATE TABLE tool_call_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_id      BIGINT       DEFAULT NULL,
    conversation_id VARCHAR(160) DEFAULT NULL,
    session_id      VARCHAR(64)  DEFAULT NULL,
    caller_user_id  BIGINT       DEFAULT NULL,
    tool_name       VARCHAR(64)  NOT NULL,
    arguments       TEXT         NOT NULL,
    -- 结果只留摘要：审计表不是数据仓库，够复盘即可
    result_summary  VARCHAR(500) DEFAULT NULL,
    -- OK / ERROR / TIMEOUT / REJECTED：超时与拒绝必须能区分出来
    status          VARCHAR(16)  NOT NULL,
    elapsed_ms      BIGINT       NOT NULL,
    trace_id        VARCHAR(64)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tool_call_audit_project (project_id, id),
    KEY idx_tool_call_audit_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pending_title_change (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    owner_id      BIGINT       NOT NULL,
    new_title     VARCHAR(128) NOT NULL,
    -- 单次有效令牌：重复使用只能生效一次
    confirm_token VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at   DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pending_title_change_token (confirm_token),
    KEY idx_pending_title_change_project (project_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
