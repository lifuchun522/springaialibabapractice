package com.example.digitalhuman.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ChatLedgerService;
import com.example.digitalhuman.service.ConversationRequest;
import com.example.digitalhuman.service.DigitalHumanChatService;
import com.example.digitalhuman.service.ProjectService;

import reactor.core.publisher.Flux;

/**
 * 数字人对话入口：阻塞式问答、流式问答、会话历史。
 *
 * <p>控制器只做参数绑定与委派：记忆、模型参数、账本都在服务层，
 * 这里不出现任何 provider 类型，也不拼消息列表。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final DigitalHumanChatService chatService;
    private final ChatLedgerService ledgerService;
    private final AuthService authService;
    private final ProjectService projectService;

    public ChatController(DigitalHumanChatService chatService,
                          ChatLedgerService ledgerService,
                          AuthService authService,
                          ProjectService projectService) {
        this.chatService = chatService;
        this.ledgerService = ledgerService;
        this.authService = authService;
        this.projectService = projectService;
    }

    /** 第 1 掌就在用的 GET 接口；不带 projectId 时走默认人设。 */
    @GetMapping("/chat")
    public String chat(@RequestParam("q") String question,
                       @RequestParam(value = "projectId", required = false) Long projectId,
                       @RequestParam(value = "sessionId", required = false) String sessionId) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 q 不能为空");
        }
        return chatService.answer(new ConversationRequest(projectId, sessionId, null, question));
    }

    public record ChatRequest(String text, String sessionId) {
    }

    public record ChatReply(String reply) {
    }

    /** 阻塞式：换模型只改数据，接口形状不变（第 4 掌的验收点）。 */
    @PostMapping("/projects/{id}/chat")
    public ChatReply chatForProject(@PathVariable("id") Long projectId,
                                    @RequestBody ChatRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text 不能为空");
        }
        String reply = chatService.answer(
                new ConversationRequest(projectId, request.sessionId(), null, request.text()));
        return new ChatReply(reply);
    }

    /**
     * 流式：{@code text/event-stream} 逐段下发。
     *
     * <p>用 GET 是为了让 curl 与前端都能直接消费；请求一旦开始，HTTP 状态码就固定了，
     * 所以流内错误改成最后一帧 {@code event: error}，而不是抛出去变成一个已经无意义的 500。
     */
    @GetMapping(value = "/projects/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamForProject(
            @PathVariable("id") Long projectId,
            @RequestParam("text") String text,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 text 不能为空");
        }
        return chatService.stream(new ConversationRequest(projectId, sessionId, null, text))
                .map(chunk -> ServerSentEvent.builder(chunk).build())
                .onErrorResume(ex -> Flux.just(ServerSentEvent.builder(String.valueOf(ex.getMessage()))
                        .event("error")
                        .build()));
    }

    /** 产品历史：按项目 + 会话查账本，与模型看到的 Memory 窗口无关。 */
    @GetMapping("/projects/{id}/sessions/{sessionId}/messages")
    public List<ChatLedgerService.MessageView> history(@RequestHeader("X-Token") String token,
                                                       @PathVariable("id") Long projectId,
                                                       @PathVariable("sessionId") String sessionId) {
        Long ownerId = authService.requireUserId(token);
        projectService.requireOwned(ownerId, projectId);
        return ledgerService.history(projectId, sessionId).stream()
                .map(ChatLedgerService.MessageView::of)
                .toList();
    }
}
