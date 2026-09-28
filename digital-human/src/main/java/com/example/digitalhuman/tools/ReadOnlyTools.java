package com.example.digitalhuman.tools;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.domain.ChatMessage;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.repository.ChatMessageRepository;
import com.example.digitalhuman.service.ProjectService;

/**
 * 只读工具集：进模型自由的对话循环，模型想调几次调几次。
 *
 * <p>两条硬约束：
 * <ol>
 *   <li><b>身份不入 Schema</b>：项目号、用户号一律从 {@link ToolContext} 旁路取，不作为方法参数；
 *       参数列表里出现身份，模型就有机会填成别人的。</li>
 *   <li><b>枚举收窄值域</b>：指标名用 Java enum，框架生成的 Schema 里就带 {@code enum} 白名单，
 *       模型能填的值被硬性限定，而不是靠「描述写得清楚」这种概率约束。</li>
 * </ol>
 */
@Component
public class ReadOnlyTools {

    /** 指标白名单：Schema 里会生成 enum，模型填不了别的。 */
    public enum SessionMetric {
        /** 消息总条数。 */
        MESSAGE_COUNT,
        /** 用户提问条数。 */
        USER_MESSAGE_COUNT,
        /** 最后一条消息时间。 */
        LAST_MESSAGE_AT
    }

    private final ProjectService projectService;
    private final ChatMessageRepository messages;

    public ReadOnlyTools(ProjectService projectService, ChatMessageRepository messages) {
        this.projectService = projectService;
        this.messages = messages;
    }

    @Tool(name = "getProjectInfo",
            description = "查询当前数字人项目的基本信息：标题、主题色、开场白、结束语以及当前使用的模型。只读操作。")
    public String getProjectInfo(ToolContext context) {
        DigitalHumanProject project = projectService.runtimeView(projectId(context)).project();
        return "项目《" + project.getTitle() + "》，主题色 " + project.getThemeColor()
                + "，开场白：「" + nullToDash(project.getOpeningLine()) + "」"
                + "，结束语：「" + nullToDash(project.getClosingLine()) + "」。";
    }

    /**
     * 确定性统计：标记 {@code returnDirect}，工具结果直接作为最终回答，
     * 模型没有机会对数字做二次改写。
     */
    @Tool(name = "querySessionStats", returnDirect = true,
            description = "统计当前数字人项目的会话数据。只读操作。可用于回答「这个会话有多少条消息」这类问题。")
    public String querySessionStats(
            @ToolParam(description = "指标：MESSAGE_COUNT=消息总条数，USER_MESSAGE_COUNT=用户提问条数，LAST_MESSAGE_AT=最后一条消息时间")
            SessionMetric metric,
            @ToolParam(description = "会话ID；留空表示统计当前会话", required = false) String sessionId,
            ToolContext context) {

        Long projectId = projectId(context);
        String effectiveSession = (sessionId == null || sessionId.isBlank()) ? sessionId(context) : sessionId;
        List<ChatMessage> history = messages.findByProjectIdAndSessionIdOrderByIdAsc(projectId, effectiveSession);

        return switch (metric) {
            case MESSAGE_COUNT -> "会话 " + effectiveSession + " 共有 " + history.size() + " 条消息。";
            case USER_MESSAGE_COUNT -> "会话 " + effectiveSession + " 中用户提问了 "
                    + history.stream().filter(message -> message.getRole() == ChatMessage.Role.USER).count() + " 次。";
            case LAST_MESSAGE_AT -> {
                LocalDateTime last = history.isEmpty() ? null : history.get(history.size() - 1).getCreatedAt();
                yield last == null
                        ? "会话 " + effectiveSession + " 还没有任何消息。"
                        : "会话 " + effectiveSession + " 最后一条消息时间是 " + last + "。";
            }
        };
    }

    static Long projectId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get(ToolContextKeys.PROJECT_ID);
        if (!(value instanceof Long id)) {
            throw new IllegalStateException("缺少项目身份：ToolContext 未注入 " + ToolContextKeys.PROJECT_ID);
        }
        return id;
    }

    static String sessionId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get(ToolContextKeys.SESSION_ID);
        return value == null ? "default" : String.valueOf(value);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "未设置" : value;
    }
}
