package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.digitalhuman.service.DigitalHumanChatService;

/**
 * 给实时语音 Bridge 用的 OpenAI 兼容端点。
 *
 * <p>Bridge 只做音频编排与协议适配，理解与推理全部压在 Spring 侧——
 * 这样 Tool / RAG / Agent 只在 Spring 这边长一遍，而不是两边各长一套。
 *
 * <p>调用方按 OpenAI 协议传 {@code messages}，并用 {@code user} 字段带上项目号（形如 {@code 12:session-1}）。
 */
@RestController
@RequestMapping("/internal/llm/v1")
public class InternalLlmController {

    private final DigitalHumanChatService chatService;

    public InternalLlmController(DigitalHumanChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat/completions")
    public Map<String, Object> completions(@RequestBody Map<String, Object> body) {
        Long projectId = parseProjectId(String.valueOf(body.getOrDefault("user", "")));
        String question = lastUserMessage(body);

        String answer = chatService.answer(projectId, question);
        return Map.of(
                "id", "chatcmpl-local",
                "object", "chat.completion",
                "model", String.valueOf(body.getOrDefault("model", "digital-human")),
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", answer),
                        "finish_reason", "stop")));
    }

    /** {@code user} 形如 {@code 12:session-1}，冒号前是项目号。 */
    private static Long parseProjectId(String user) {
        String head = user.split(":", 2)[0].trim();
        if (head.isEmpty()) {
            throw new IllegalArgumentException("请求体缺少 user 字段（需要形如 {projectId}:{sessionId}）");
        }
        try {
            return Long.valueOf(head);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("user 字段的项目号不是数字：" + head);
        }
    }

    private static String lastUserMessage(Map<String, Object> body) {
        Object messages = body.get("messages");
        if (!(messages instanceof List<?> list)) {
            throw new IllegalArgumentException("请求体缺少 messages 数组");
        }
        String text = "";
        for (Object item : list) {
            if (item instanceof Map<?, ?> message && "user".equals(message.get("role"))) {
                Object content = message.get("content");
                text = content instanceof String str ? str : String.valueOf(content);
            }
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("messages 里没有 user 内容");
        }
        return text;
    }
}
