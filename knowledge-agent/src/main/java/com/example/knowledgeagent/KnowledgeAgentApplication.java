package com.example.knowledgeagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 知识 Agent：**独立进程、独立数据、只通过 A2A 协议被调用**。
 *
 * <p>它和 digital-human-mcp 的区别正是这一掌要讲清楚的边界：
 * MCP Server 提供的是「能力」（无状态、无自主）：输入查询、输出片段；
 * 知识 Agent 是一个**主体**：它自己决定查什么、命中不够时怎么回答、要不要说明「资料里没有」。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAgentApplication.class, args);
    }
}
