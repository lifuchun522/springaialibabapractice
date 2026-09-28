package com.example.digitalhuman.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 展厅预约 MCP Server。
 *
 * <p>它独立部署、独立数据、独立发布：调用方（数字人服务、以后可能的其它产品线）
 * 只依赖工具描述与数据契约，不依赖我们的代码。
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    /** 把 @Tool 对象注册成 MCP 工具，协议侧不认识 Java，只认识工具定义。 */
    @Bean
    ToolCallbackProvider showroomToolCallbackProvider(ShowroomBookingTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
