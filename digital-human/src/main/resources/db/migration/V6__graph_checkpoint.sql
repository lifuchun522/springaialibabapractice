-- 第 11 掌：图编排的检查点（可中断、可恢复、进程重启后仍拿得到）
--
-- 为什么要自己建表，而不是用内存 saver：验收第四条是「状态可持久化」，
-- 内存 saver 在进程重启后就什么都没有了——本文第一版就是栽在这里。
--
-- 表结构对齐框架自带的 PostgresSaver（GraphThread / GraphCheckpoint），
-- 只是换成 MySQL 的类型：state_data 用 longblob 存序列化后的状态 + 一个 content_type 记序列化格式，
-- 这样以后换序列化器（Jackson / JDK）不需要改表。
create table graph_thread
(
    thread_id   varchar(64)  not null comment '图执行的会话标识（RunnableConfig.threadId）',
    thread_name varchar(255) null comment '可读名，便于排查；不参与索引',
    is_released tinyint(1)   not null default 0 comment '是否已释放：释放后该 thread 的检查点不再用于恢复',
    created_at  datetime(3)  not null default current_timestamp(3),
    primary key (thread_id)
) engine = InnoDB
  default charset = utf8mb4 comment '图执行的会话';

create table graph_checkpoint
(
    -- 自增序号是**恢复能否成功的关键**：检查点是「按写入顺序的日志」，
    -- 用 saved_at 排序不行——同一毫秒内会写好几条，排序就变成随机的了。
    -- （第一版就是这么写的，表现为「恢复时从第一个检查点重跑」，见 docs/ch11-验收记录.md）
    seq                bigint       not null auto_increment,
    checkpoint_id      varchar(64)  not null comment '检查点 id（框架生成）',
    thread_id          varchar(64)  not null comment '所属会话',
    node_id            varchar(255) null comment '执行到哪个节点',
    next_node_id       varchar(255) null comment '下一个要执行的节点：中断时它指向断点',
    state_data         longblob     not null comment '序列化后的状态',
    state_content_type varchar(100) not null comment '序列化格式（换序列化器不用改表）',
    saved_at           datetime(3)  not null default current_timestamp(3),
    primary key (seq),
    unique key uk_graph_ckpt_id (checkpoint_id),
    key idx_graph_ckpt_thread (thread_id, seq),
    constraint fk_graph_ckpt_thread foreign key (thread_id) references graph_thread (thread_id) on delete cascade
) engine = InnoDB
  default charset = utf8mb4 comment '图的检查点快照';
