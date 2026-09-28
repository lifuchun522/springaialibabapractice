-- 测试库（H2）里的图检查点表：与 Flyway V6 等价，只把类型换成 H2 也认的写法。
-- 为什么测试不直接跑 Flyway：第 3 掌起测试 profile 就走 Hibernate 建表 + Flyway 关闭
-- （保证 `./mvnw test` 离线可跑、结果确定）。图的检查点不在 JPA 实体里，所以单独建一次。
create table if not exists graph_thread
(
    thread_id   varchar(64)  not null,
    thread_name varchar(255) null,
    is_released tinyint      not null default 0,
    created_at  timestamp(3) not null default current_timestamp,
    primary key (thread_id)
);

create table if not exists graph_checkpoint
(
    -- 与 Flyway V6 一致：按自增序号排序（按时间戳排会在同一毫秒内乱序）
    seq                bigint       not null auto_increment,
    checkpoint_id      varchar(64)  not null,
    thread_id          varchar(64)  not null,
    node_id            varchar(255) null,
    next_node_id       varchar(255) null,
    state_data         blob         not null,
    state_content_type varchar(100) not null,
    saved_at           timestamp(3) not null default current_timestamp,
    primary key (seq)
);
