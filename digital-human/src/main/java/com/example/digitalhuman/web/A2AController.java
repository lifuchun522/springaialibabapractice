package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.a2a.A2AClient;
import com.example.digitalhuman.a2a.AgentRegistry;

/**
 * 跨服务协作入口：主服务把「知识」这件事交给远端的知识 Agent。
 *
 * <p>响应里刻意带上三样东西，因为这一掌的三条验收都只能靠它们证明：
 * <ul>
 *   <li>{@code instanceId}：这次打到了哪个实例（多实例是否被分散调用）；</li>
 *   <li>{@code traceId} / {@code taskId}：跨服务贯穿标识（两条日志能对上）；</li>
 *   <li>{@code deltas} / {@code elapsedMs}：流式片段数与耗时（部分结果确实被消费了）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projects/{id}/a2a")
public class A2AController {

    private final A2AClient a2aClient;
    private final AgentRegistry registry;

    public A2AController(A2AClient a2aClient, AgentRegistry registry) {
        this.a2aClient = a2aClient;
        this.registry = registry;
    }

    public record AskRequest(String question) {
    }

    @PostMapping("/ask")
    public A2AClient.RemoteAnswer ask(@PathVariable("id") Long projectId,
                                      @RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }
        return a2aClient.ask(request.question());
    }

    /** 当前发现的实例列表：注册中心（或静态表）里到底有什么，调用方应该看得到。 */
    @GetMapping("/instances")
    public Map<String, Object> instances(@PathVariable("id") Long projectId) {
        List<AgentRegistry.AgentInstance> found = registry.instances();
        return Map.of("count", found.size(), "instances", found);
    }
}
