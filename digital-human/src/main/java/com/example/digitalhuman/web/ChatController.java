package com.example.digitalhuman.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.service.DigitalHumanChatService;

/**
 * 数字人对话入口。
 *
 * <p>控制器只做参数校验与委派：System Prompt 从项目配置取，模型调用走 ChatClient 唯一出口。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final DigitalHumanChatService chatService;

    public ChatController(DigitalHumanChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * @param question  用户问题
     * @param projectId 数字人项目 id；不带则使用全局默认人设（第 1 掌的行为）
     */
    @GetMapping("/chat")
    public String chat(@RequestParam("q") String question,
                       @RequestParam(value = "projectId", required = false) Long projectId) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 q 不能为空");
        }
        return chatService.answer(projectId, question);
    }
}
