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

import com.example.digitalhuman.config.SseHeartbeat;
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
    private final SseHeartbeat sseHeartbeat;

    public ChatController(DigitalHumanChatService chatService,
                          ChatLedgerService ledgerService,
                          AuthService authService,
                          ProjectService projectService,
                          SseHeartbeat sseHeartbeat) {
        this.chatService = chatService;
        this.ledgerService = ledgerService;
        this.authService = authService;
        this.projectService = projectService;
        this.sseHeartbeat = sseHeartbeat;
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

    public record ChatRequest(String text, String sessionId, Boolean allowWrite) {
    }

    public record ChatReply(String reply) {
    }

    /**
     * 阻塞式：换模型只改数据，接口形状不变（第 4 掌的验收点）。
     *
     * <p>写能力（{@code allowWrite=true}）必须带登录身份：没有身份就不发写工具，
     * 免得待确认记录挂在一个假 owner 名下、谁也确认不了。
     *
     * <p>第 16 掌把 {@code produces} 显式写出来的原因：这是**契约的一部分**。
     * 不写时前端也能跑（Spring 会按返回值协商），但「能跑」和「说清楚」是两件事——
     * 前端联调时第一个问题永远是「返回什么类型、字段叫什么」，而答案应该在 Controller 上就能读到。
     */
    @PostMapping(value = "/projects/{id}/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chatForProject(@RequestHeader(value = "X-Token", required = false) String token,
                                    @PathVariable("id") Long projectId,
                                    @RequestBody ChatRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text 不能为空");
        }
        boolean allowWrite = Boolean.TRUE.equals(request.allowWrite());
        Long userId = allowWrite ? authService.requireUserId(token) : null;
        String reply = chatService.answer(new ConversationRequest(projectId, request.sessionId(), userId,
                request.text(), allowWrite));
        return new ChatReply(reply);
    }

    /**
     * 流式：{@code text/event-stream} 逐段下发。
     *
     * <p>用 GET 是为了让 curl 与前端都能直接消费；请求一旦开始，HTTP 状态码就固定了，
     * 所以流内错误改成最后一帧 {@code event: error}，而不是抛出去变成一个已经无意义的 500。
     *
     * <p>第 16 掌补的那一半是**心跳**：模型思考或工具执行期间可能几十秒没有下行数据，
     * 而任何一层网关的空闲超时都会把这种静默判成死连接——直连正常、过网关就断的
     * 「假流式」就是这么来的。心跳帧是注释行，客户端按 SSE 规范直接忽略，
     * 它只负责让链路看到连接还活着（见 {@code SseHeartbeat}）。
     */
    @GetMapping(value = "/projects/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamForProject(
            @RequestHeader(value = "X-Token", required = false) String token,
            @PathVariable("id") Long projectId,
            @RequestParam("text") String text,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "allowWrite", required = false, defaultValue = "false") boolean allowWrite) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 text 不能为空");
        }
        // 与阻塞式同一条规则：写能力必须带登录身份
        Long userId = allowWrite ? authService.requireUserId(token) : null;
        Flux<ServerSentEvent<String>> tokens =
                chatService.stream(new ConversationRequest(projectId, sessionId, userId, text, allowWrite))
                        .map(chunk -> ServerSentEvent.builder(chunk).build())
                        .onErrorResume(ex -> Flux.just(ServerSentEvent.builder(String.valueOf(ex.getMessage()))
                                .event("error")
                                .build()));
        return sseHeartbeat.attach(tokens);
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
