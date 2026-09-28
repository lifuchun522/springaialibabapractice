-- 知识 Agent 自己的数据：主服务没有这张表，想拿知识只能走 A2A 协议
create table knowledge_agent_document
(
    id       bigint       not null auto_increment,
    doc_name varchar(255) not null,
    content  text         not null,
    primary key (id)
) engine = InnoDB
  default charset = utf8mb4 comment '知识 Agent 的文档真源';
