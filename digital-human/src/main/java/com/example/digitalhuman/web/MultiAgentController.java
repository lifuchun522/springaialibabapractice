package com.example.digitalhuman.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.multiagent.MultiAgentService;

/**
 * 多 Agent 入口。
 *
 * <p>对外仍然是一个字符串（`reply`）；多出来的是这一轮的**路由轨迹**：
 * 第一跳是谁（`route`）、走过哪些角色（`hops`）、各角色记忆里有多少条（`memorySizes`）、
 * 每个角色名下的工具（`roleTools`）。
 *
 * <p>把这些暴露出来不是为了好看：这一掌的三条验收里，有两条（工具边界、记忆边界）
 * 只能靠「看它到底带了多少东西」来证明。
 */
@RestController
@RequestMapping("/api/projects/{id}/multi-agent")
public class MultiAgentController {

    private final MultiAgentService multiAgentService;

    public MultiAgentController(MultiAgentService multiAgentService) {
        this.multiAgentService = multiAgentService;
    }

    public record AskRequest(String question, String sessionId) {
    }

    @PostMapping("/ask")
    public MultiAgentService.MultiAgentAnswer ask(@PathVariable("id") Long projectId,
                                                  @RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }
        return multiAgentService.ask(projectId, request.sessionId(), request.question());
    }
}
