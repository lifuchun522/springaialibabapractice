-- 一键启动用的库初始化：只建库，不建表。
--
-- 表全部由两个应用自己跑 Flyway 迁移建立（digital-human 用 V1～V6，
-- digital-human-mcp 用它的 V1），这样「库结构是谁负责的」始终只有一个答案：
-- 迁移脚本在应用里，不在这份 SQL 里。
--
-- MySQL 官方镜像只在数据卷为空时执行 /docker-entrypoint-initdb.d/ 下的脚本，
-- 所以这份脚本天然是「首次启动才跑」，重复执行也不会重复建库。

CREATE DATABASE IF NOT EXISTS digital_human
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- MCP Server 有自己的库，不与主应用共用一个 schema
CREATE DATABASE IF NOT EXISTS digital_human_ext
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
