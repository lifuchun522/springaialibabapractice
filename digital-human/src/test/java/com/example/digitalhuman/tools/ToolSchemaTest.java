package com.example.digitalhuman.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import com.example.digitalhuman.repository.ChatMessageRepository;
import com.example.digitalhuman.service.ProjectService;
import com.example.digitalhuman.service.TitleChangeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 工具的 JSON Schema 就是模型的「接口文档」。
 *
 * <p>这一组断言把两件事钉死：
 * <ol>
 *   <li>受限值域要落成 {@code enum} 白名单，而不是靠描述里的自然语言（描述是概率约束，enum 是硬约束）；</li>
 *   <li>身份参数绝不能出现在 Schema 里——模型看不见，也就填不了。
 * </ol>
 */
class ToolSchemaTest {

    private final ReadOnlyTools readOnlyTools =
            new ReadOnlyTools(mock(ProjectService.class), mock(ChatMessageRepository.class));
    private final WriteTools writeTools = new WriteTools(mock(TitleChangeService.class));

    private static ToolCallback callbackNamed(ToolCallback[] callbacks, String name) {
        return java.util.Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有找到工具：" + name));
    }

    @Test
    @DisplayName("schema_shouldCarryEnumWhitelistForConstrainedParameter")
    void schema_shouldCarryEnumWhitelistForConstrainedParameter() {
        String schema = callbackNamed(ToolCallbacks.from(readOnlyTools), "querySessionStats")
                .getToolDefinition().inputSchema();

        assertThat(schema).contains("MESSAGE_COUNT").contains("USER_MESSAGE_COUNT").contains("LAST_MESSAGE_AT");
        assertThat(schema).contains("\"enum\"");
    }

    @Test
    @DisplayName("schema_shouldNeverContainIdentityParameters")
    void schema_shouldNeverContainIdentityParameters() {
        for (ToolCallback callback : ToolCallbacks.from(readOnlyTools, writeTools)) {
            String schema = callback.getToolDefinition().inputSchema();
            assertThat(schema)
                    .as("工具 %s 的 Schema 不应出现身份字段", callback.getToolDefinition().name())
                    .doesNotContain("projectId")
                    .doesNotContain("userId")
                    .doesNotContain("tenantId")
                    .doesNotContain("ownerId");
        }
    }

    @Test
    @DisplayName("deterministicTool_shouldBeReturnDirectSoModelCannotRewriteNumbers")
    void deterministicTool_shouldBeReturnDirectSoModelCannotRewriteNumbers() {
        ToolCallback stats = callbackNamed(ToolCallbacks.from(readOnlyTools), "querySessionStats");
        ToolCallback projectInfo = callbackNamed(ToolCallbacks.from(readOnlyTools), "getProjectInfo");

        assertThat(stats.getToolMetadata().returnDirect()).isTrue();
        assertThat(projectInfo.getToolMetadata().returnDirect()).isFalse();
    }

    @Test
    @DisplayName("writeTool_shouldDeclareOptionalConfirmToken")
    void writeTool_shouldDeclareOptionalConfirmToken() {
        ToolCallback write = callbackNamed(ToolCallbacks.from(writeTools), "proposeTitleChange");
        String schema = write.getToolDefinition().inputSchema();

        assertThat(schema).contains("newTitle").contains("confirmToken");
        // confirmToken 必须可选：不然模型会自己编一个令牌填进去
        assertThat(schema).doesNotContain("\"required\":[\"newTitle\",\"confirmToken\"]");
    }
}
