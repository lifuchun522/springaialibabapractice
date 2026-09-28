-- 第 5 掌：把「模型短期看什么」和「产品历史记了什么」分成两份东西。
--   agent_config / ChatMemory 是投影：只决定模型这一轮看到什么，可以丢、可以重建、可以换窗口大小。
--   chat_message 是账本：产品历史必须能按 projectId/sessionId 查回来，且要能解释「半截消息」。
-- 所以本掌不改第 3 掌冻结的三张表，新增一张只追加的消息账本。

CREATE TABLE chat_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL,
    -- conversationId 的组合规则：userId:projectId:sessionId（见 ConversationId）
    conversation_id VARCHAR(160) NOT NULL,
    session_id      VARCHAR(64)  NOT NULL,
    user_id         BIGINT       DEFAULT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    -- COMPLETED / CANCELLED / FAILED：取消不是「什么都没发生」，要留下状态
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_message_project_session (project_id, session_id, id),
    KEY idx_chat_message_conversation (conversation_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
