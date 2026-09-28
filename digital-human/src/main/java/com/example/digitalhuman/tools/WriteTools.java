package com.example.digitalhuman.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.domain.PendingTitleChange;
import com.example.digitalhuman.service.ConfirmationRejectedException;
import com.example.digitalhuman.service.TitleChangeService;

/**
 * 写工具集：与只读工具分开成独立 Bean，默认不交给模型。
 *
 * <p>它自己也不直接改库：没有确认令牌时只生成一条待确认记录，
 * 真正的落库走人类的确认路径。模型对「讨论」和「指令」的区分是概率性的，
 * 所以门禁必须建立在结构上，而不是提示词上。
 */
@Component
public class WriteTools {

    private final TitleChangeService titleChangeService;

    public WriteTools(TitleChangeService titleChangeService) {
        this.titleChangeService = titleChangeService;
    }

    @Tool(name = "proposeTitleChange",
            description = "提出修改当前数字人项目标题的变更申请。不会立即生效：会返回一个确认令牌，"
                    + "必须由人类确认后才会真正修改。若人类已确认并提供了确认令牌，则本次调用直接生效。")
    public String proposeTitleChange(
            @ToolParam(description = "新的项目标题，1~60 个字符") String newTitle,
            @ToolParam(description = "确认令牌；仅当人类已明确确认本次修改时填写", required = false) String confirmToken,
            ToolContext context) {

        Long projectId = ReadOnlyTools.projectId(context);
        Long userId = userId(context);
        if (userId == null) {
            return "写操作被拒绝：当前会话没有登录身份，无法确认变更。";
        }

        if (confirmToken == null || confirmToken.isBlank()) {
            PendingTitleChange change = titleChangeService.propose(projectId, userId, newTitle);
            return "已生成待确认变更：把项目标题改为「" + change.getNewTitle() + "」。"
                    + "确认令牌：" + change.getConfirmToken() + "。此变更尚未生效，需由人类确认。";
        }

        try {
            return "标题已修改为「" + titleChangeService.confirm(projectId, userId, confirmToken).getTitle() + "」。";
        } catch (ConfirmationRejectedException ex) {
            return "确认失败：" + ex.getMessage() + "。没有产生任何数据变更。";
        }
    }

    private static Long userId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get(ToolContextKeys.USER_ID);
        return value instanceof Long id ? id : null;
    }
}
