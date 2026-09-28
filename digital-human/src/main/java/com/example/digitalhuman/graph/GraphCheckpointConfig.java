package com.example.digitalhuman.graph;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图检查点的装配。
 *
 * <p>为什么放在配置里而不是随手 new：检查点存储是**图运行时的依赖**，
 * 换实现（内存 → MySQL → Redis）应该只改这一处，图本身不动。
 * 这也是文章里「saver 类型以框架的实现为准」那句话在工程上的落法。
 */
@Configuration
public class GraphCheckpointConfig {

    /**
     * 落到 MySQL 的检查点 saver。
     *
     * <p>为什么不用框架自带的 MemorySaver：验收第四条是「进程重启后仍然拿得到」，
     * 内存实现在重启后什么都没有——卡在等人工确认的工单会全部回到起点。
     */
    @Bean
    MysqlCheckpointSaver mysqlCheckpointSaver(DataSource dataSource) {
        return new MysqlCheckpointSaver(dataSource);
    }
}
