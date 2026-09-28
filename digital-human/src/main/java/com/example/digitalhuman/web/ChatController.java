package com.example.digitalhuman.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 数字人对话入口。
 *
 * <p>控制器只做参数校验与委派，不持有模型对象，也不拼消息数组。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam("q") String question) {
        if (!StringUtils.hasText(question)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 q 不能为空");
        }
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
