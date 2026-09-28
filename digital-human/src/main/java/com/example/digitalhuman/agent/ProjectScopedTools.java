package com.example.digitalhuman.agent;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.repository.ChatMessageRepository;
import com.example.digitalhuman.service.ProjectKnowledgeRetriever;
import com.example.digitalhuman.service.ProjectService;
import com.example.digitalhuman.tools.ToolRegistry;

/**
 * 请求内作用域的工具：把「项目身份」闭包进工具对象，而不是让模型填。
 *
 * <p>做法很直白：同一套 {@code @Tool} 方法，按请求重新绑定身份。
 * 好处是模型看不到也填不了 projectId / sessionId；代价是不能复用同一个 Agent 实例
 * （见 DigitalHumanAgentService 的说明与 docs/ch09-验收记录.md 的遗留问题）。
 *
 * <p>本掌的工具里多了知识检索：第 8 掌的 RAG 是「独立接口」，
 * 这里把它变成 Agent 循环里可自主决定的一次动作——这正是从 ChatClient 换成 Agent 的实质变化：
 * 「调不调检索、调几次」由模型在边界内决定，而不是由我们写死在代码里。
 */
@Component
public class ProjectScopedTools {

    private final ProjectService projectService;
    private final ChatMessageRepository messages;
    private final ProjectKnowledgeRetriever retriever;
    private final ToolRegistry toolRegistry;

    public ProjectScopedTools(ProjectService projectService,
                              ChatMessageRepository messages,
                              ProjectKnowledgeRetriever retriever,
                              ToolRegistry toolRegistry) {
        this.projectService = projectService;
        this.messages = messages;
        this.retriever = retriever;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 为一次请求生成工具集合（已包上审计与超时——Agent 的工具调用同样要留痕）。
     */
    public ToolCallback[] callbacksFor(Long projectId, String sessionId, String traceId) {
        Bound bound = new Bound(projectId, sessionId, traceId);
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(bound)
                .build()
                .getToolCallbacks();
        return toolRegistry.wrapForAudit(callbacks);
    }

    /** 请求内绑定的工具实现：身份来自构造参数，不进 JSON Schema。 */
    public class Bound {

        private final Long projectId;
        private final String sessionId;
        private final String traceId;

        Bound(Long projectId, String sessionId, String traceId) {
            this.projectId = projectId;
            this.sessionId = sessionId;
            this.traceId = traceId;
        }

        @Tool(name = "project_info", description = "查询当前数字人项目的标题、开场白与结束语。只读操作。")
        public String projectInfo() {
            DigitalHumanProject project = projectService.runtimeView(projectId).project();
            return "项目《" + project.getTitle() + "》，开场白：「" + orDash(project.getOpeningLine())
                    + "」，结束语：「" + orDash(project.getClosingLine()) + "」。";
        }

        @Tool(name = "session_stats",
                description = "统计当前会话的消息条数，用于回答「这个会话有多少条消息」这类问题。只读操作。")
        public String sessionStats(
                @ToolParam(description = "指标：MESSAGE_COUNT=消息总条数，USER_MESSAGE_COUNT=用户提问条数")
                SessionMetric metric) {
            List<ChatMessage> history = messages.findByProjectIdAndSessionIdOrderByIdAsc(projectId, sessionId);
            return switch (metric) {
                case USER_MESSAGE_COUNT -> "当前会话里用户提了 "
                        + history.stream().filter(m -> m.getRole() == ChatMessage.Role.USER).count() + " 次问题。";
                case MESSAGE_COUNT -> "当前会话共有 " + history.size() + " 条消息。";
            };
        }

        @Tool(name = "knowledge_search",
                description = "在当前项目的知识库里检索资料并返回带出处的片段；参数 question 是要检索的问题。只读操作。")
        public String knowledgeSearch(
                @ToolParam(description = "要检索的问题，例如「展厅开放时间是几点」") String question) {
            List<Document> hits = retriever.retrieve(projectId, question);
            if (hits.isEmpty()) {
                return "知识库里没有与该问题相关的内容。";
            }
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < hits.size(); index++) {
                Document hit = hits.get(index);
                builder.append('[').append(index + 1).append("] 来源=")
                        .append(hit.getMetadata().get("docName")).append('#')
                        .append(hit.getMetadata().get("chunkIndex")).append('\n')
                        .append(hit.getText()).append("\n\n");
            }
            return builder.toString().trim();
        }

        private static String orDash(String value) {
            return value == null || value.isBlank() ? "未设置" : value;
        }
    }

    /** 指标白名单：Schema 里会带 enum，模型填不了别的值。 */
    public enum SessionMetric {
        MESSAGE_COUNT,
        USER_MESSAGE_COUNT
    }
}
