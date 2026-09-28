package com.example.digitalhuman.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.digitalhuman.agent.DigitalHumanAgentService;

/**
 * Agent 主链路入口。
 *
 * <p>交付口径（第 9 掌验收第 4 条）：**调用方拿到的仍然是一个字符串** ——
 * 换成 Agent 之后，STT → Agent → TTS 这条链路对外表现不变；
 * traceId / modelCalls / events 是额外的可观测字段，客户端可以忽略。
 */
@RestController
@RequestMapping("/api/projects/{id}/agent")
public class AgentController {

    private final DigitalHumanAgentService agentService;

    public AgentController(DigitalHumanAgentService agentService) {
        this.agentService = agentService;
    }

    public record AgentChatRequest(String question, String sessionId) {
    }

    @PostMapping("/chat")
    public DigitalHumanAgentService.AgentAnswer chat(@PathVariable("id") Long projectId,
                                                     @RequestBody AgentChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }
        return agentService.answer(projectId, request.sessionId(), request.question());
    }
}
