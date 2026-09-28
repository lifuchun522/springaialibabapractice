package com.example.digitalhuman.tools;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后主动拉一次远程工具清单。
 *
 * <p>为什么这件事必须在启动期做：MCP Client 连不上 Server 时**不会报错**，
 * 只会得到「一个工具都没有」。这种静默失败在运行期的表现是「模型忽然不会查预约了」，
 * 而根因（Server 没起、协议模式配错、路径写错）在日志里一个字都没有。
 *
 * <p>所以这里做两件事：把发现的工具名打进启动日志；清单为空时按配置直接失败。
 */
@Component
public class McpDiscoveryCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpDiscoveryCheck.class);

    private final ToolRegistry toolRegistry;
    private final boolean failFast;

    public McpDiscoveryCheck(ToolRegistry toolRegistry,
                             @Value("${digital-human.mcp.fail-fast:true}") boolean failFast) {
        this.toolRegistry = toolRegistry;
        this.failFast = failFast;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] names = Arrays.stream(toolRegistry.remote())
                .map(callback -> callback.getToolDefinition().name())
                .toArray(String[]::new);

        if (names.length == 0) {
            String reason = "MCP 远程工具清单为空。常见原因：Server 未启动、客户端端点与 Server 协议不一致 "
                    + "（STREAMABLE 对应 streamable-http 的 /mcp）、或者网络不可达。";
            if (failFast) {
                throw new IllegalStateException(reason);
            }
            log.warn("{}（digital-human.mcp.fail-fast=false，继续启动，但远程能力不可用）", reason);
            return;
        }
        log.info("MCP 远程工具已发现 {} 个：{}", names.length, String.join("、", names));
    }
}
