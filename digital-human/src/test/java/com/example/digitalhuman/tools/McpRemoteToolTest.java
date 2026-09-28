package com.example.digitalhuman.tools;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.ToolCallAuditRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 远程（MCP）工具的挂载、审计与启动期发现检查。 */
class McpRemoteToolTest {

    private static ToolCallback remoteCallback(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("远程工具").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "{\"status\":\"OK\"}";
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ToolCallbackProvider> providerOf(ToolCallback... callbacks) {
        ToolCallbackProvider provider = () -> callbacks;
        ObjectProvider<ToolCallbackProvider> providerSource = mock(ObjectProvider.class);
        when(providerSource.orderedStream()).thenReturn(Stream.of(provider));
        return providerSource;
    }

    private static ToolRegistry registryWith(ObjectProvider<ToolCallbackProvider> remoteProviders,
                                             ExecutorService executor, ToolCallAuditRepository audits) {
        return new ToolRegistry(new ReadOnlyTools(mock(com.example.digitalhuman.service.ProjectService.class),
                mock(com.example.digitalhuman.repository.ChatMessageRepository.class)),
                new WriteTools(mock(com.example.digitalhuman.service.TitleChangeService.class)),
                audits, executor, 1000, remoteProviders);
    }

    @Test
    @DisplayName("remote_shouldExposeDiscoveredRemoteToolsAndAuditThem")
    void remote_shouldExposeDiscoveredRemoteToolsAndAuditThem() {
        ToolCallAuditRepository audits = mock(ToolCallAuditRepository.class);
        when(audits.save(any(ToolCallAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolRegistry registry = registryWith(
                    providerOf(remoteCallback("showroom_query_availability")), executor, audits);

            assertThat(registry.remote()).hasSize(1);
            String result = registry.remote()[0].call("{\"showroom\":\"深圳展厅\"}",
                    new org.springframework.ai.chat.model.ToolContext(
                            java.util.Map.of(ToolContextKeys.PROJECT_ID, 1L, ToolContextKeys.TRACE_ID, "trace-mcp")));

            // 远程调用同样要留痕：出问题时两边都要对账
            assertThat(result).contains("OK");
            var captor = org.mockito.ArgumentCaptor.forClass(ToolCallAudit.class);
            org.mockito.Mockito.verify(audits).save(captor.capture());
            assertThat(captor.getValue().getToolName()).isEqualTo("showroom_query_availability");
            assertThat(captor.getValue().getStatus()).isEqualTo(ToolCallAudit.Status.OK);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("remote_shouldBeEmptyWhenMcpClientIsNotConfigured")
    void remote_shouldBeEmptyWhenMcpClientIsNotConfigured() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolRegistry registry = registryWith(providerOf(), executor, mock(ToolCallAuditRepository.class));

            assertThat(registry.remote()).isEmpty();
            assertThat(registry.readOnly()).isNotEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("discoveryCheck_shouldFailFastWhenRemoteToolListIsEmpty")
    void discoveryCheck_shouldFailFastWhenRemoteToolListIsEmpty() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolRegistry empty = registryWith(providerOf(), executor, mock(ToolCallAuditRepository.class));

            // Server 没起来时，「工具静默为空」比启动失败更难查
            assertThatThrownBy(() -> new McpDiscoveryCheck(empty, true).run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MCP 远程工具清单为空")
                    .hasMessageContaining("streamable-http");

            // 关掉 fail-fast 时只告警，不拦启动
            new McpDiscoveryCheck(empty, false).run(null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("discoveryCheck_shouldPassWhenRemoteToolsExist")
    void discoveryCheck_shouldPassWhenRemoteToolsExist() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ToolRegistry registry = registryWith(providerOf(remoteCallback("showroom_query_availability")),
                    executor, mock(ToolCallAuditRepository.class));

            new McpDiscoveryCheck(registry, true).run(null);   // 不抛异常即通过

            assertThat(List.of(registry.remote()).get(0).getToolDefinition().name())
                    .isEqualTo("showroom_query_availability");
        } finally {
            executor.shutdownNow();
        }
    }
}
