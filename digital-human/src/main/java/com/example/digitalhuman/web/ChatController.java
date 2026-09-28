package com.example.digitalhuman.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.service.DigitalHumanChatService;

/**
 * 数字人对话入口：运行页用的 POST 接口，以及第 1 掌就在用的 GET 接口。
 *
 * <p>控制器只做参数校验与委派：System Prompt 与模型参数都从项目配置取，
 * 模型调用走 ChatClient 唯一出口。
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

    public record ChatRequest(String text) {
    }

    public record ChatReply(String reply) {
    }

    /** 运行页与后台都用这一个接口：换模型只改数据，接口形状不变。 */
    @PostMapping("/projects/{id}/chat")
    public ChatReply chatForProject(@PathVariable("id") Long projectId,
                                    @RequestBody ChatRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text 不能为空");
        }
        return new ChatReply(chatService.answer(projectId, request.text()));
    }
}
