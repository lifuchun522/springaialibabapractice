-- 第 4 掌：把「用哪个模型」从代码挪进数据。
-- provider 才是路由键，model 只是它下面的一个普通字符串——两者混成一个字段，换模型就变成改代码。

ALTER TABLE agent_config
    ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'deepseek' AFTER project_id;
