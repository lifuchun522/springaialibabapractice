package com.example.knowledgeagent.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.knowledgeagent.A2AProtocol;
import com.example.knowledgeagent.KnowledgeBase;

import reactor.core.publisher.Flux;

/**
 * A2A Server 端：能力声明 + 任务生命周期 + 流式事件。
 *
 * <p>版本协商是**显式**的（验收第四条）：请求头里的协议版本与服务端不一致时直接 409，
 * 而不是「尽力解析后给出一个看起来对的答案」——后者是最贵的失败模式：
 * 调用方以为成功了，业务上却拿到了错的语义。
 */
@RestController
public class A2AAgentController {

    private static final Logger log = LoggerFactory.getLogger(A2AAgentController.class);

    private final KnowledgeBase knowledgeBase;
    private final KnowledgeBase.TaskStore tasks;
    private final ChatModel chatModel;
    private final String instanceId;
    private final int port;

    public A2AAgentController(KnowledgeBase knowledgeBase,
                              KnowledgeBase.TaskStore tasks,
                              ChatModel chatModel,
                              @Value("${a2a.instance-id:local}") String instanceId,
                              @Value("${server.port:8082}") int port) {
        this.knowledgeBase = knowledgeBase;
        this.tasks = tasks;
        this.chatModel = chatModel;
        this.instanceId = instanceId;
        this.port = port;
    }

    public record AskRequest(String question) {
    }

    /** 能力声明：主服务靠它知道「你是谁、你会什么、你怎么被访问」。 */
    @GetMapping("/.well-known/agent.json")
    public A2AProtocol.AgentCard card() {
        return new A2AProtocol.AgentCard(
                "knowledge-agent",
                "项目知识 Agent：多轮澄清 + 检索 + 组织成一段可讲解的答案（不是纯检索工具）",
                A2AProtocol.VERSION,
                "http://127.0.0.1:" + port,
                List.of(new A2AProtocol.AgentCard.Skill("knowledge-qa", "项目知识问答",
                        "基于本服务已索引的文档回答，命中不足时明确说明资料里没有")),
                new A2AProtocol.AgentCard.Capabilities(true, true));
    }

    /** 创建任务：返回 **202 + 任务号**，而不是同步等结果（远端不确定性要进本地状态机）。 */
    @PostMapping("/a2a/tasks")
    public ResponseEntity<A2AProtocol.Task> create(
            @RequestHeader(value = A2AProtocol.VERSION_HEADER, required = false) String version,
            @RequestHeader(value = A2AProtocol.TRACE_HEADER, required = false) String traceId,
            @RequestBody AskRequest request) {
        requireCompatibleVersion(version);
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }
        String trace = traceId == null || traceId.isBlank() ? "no-trace" : traceId;
        A2AProtocol.Task task = tasks.create(trace, request.question());
        log.info("[a2a] trace={} task={} instance={} 收到任务：{}",
                trace, task.taskId(), instanceId, request.question());
        return ResponseEntity.accepted().body(task);
    }

    /** 任务状态：调用方随时能问「那件事到哪一步了」。 */
    @GetMapping("/a2a/tasks/{taskId}")
    public A2AProtocol.Task get(@PathVariable("taskId") String taskId) {
        return tasks.get(taskId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在：" + taskId));
    }

    /** 取消：协议层要定义「谁有权取消」，否则悬挂任务没人清理。 */
    @PostMapping("/a2a/tasks/{taskId}/cancel")
    public A2AProtocol.Task cancel(@PathVariable("taskId") String taskId) {
        if (!tasks.cancel(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在：" + taskId);
        }
        log.info("[a2a] task={} 已取消", taskId);
        return tasks.get(taskId).orElseThrow();
    }

    /**
     * 流式执行：先回状态事件，再逐段回内容，最后回终态。
     *
     * <p>为什么流式不是锦上添花：数字人的体验取决于首字延迟，
     * 「部分结果」必须是一种合法的、可被消费的中间状态，而不是异常。
     */
    @PostMapping(value = "/a2a/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<A2AProtocol.TaskEvent> events(@PathVariable("taskId") String taskId,
                                             @RequestHeader(value = A2AProtocol.TRACE_HEADER,
                                                     required = false) String traceId) {
        A2AProtocol.Task task = tasks.get(taskId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在：" + taskId));
        return Flux.defer(() -> {
            List<Map<String, String>> hits = knowledgeBase.search(task.question(), 3);
            tasks.update(taskId, A2AProtocol.TaskState.WORKING, null, hits, null);
            log.info("[a2a] trace={} task={} instance={} hits={}",
                    task.traceId(), taskId, instanceId, hits.size());

            String answer = compose(task.question(), hits);
            tasks.update(taskId, A2AProtocol.TaskState.COMPLETED, answer, hits, null);
            log.info("[a2a] trace={} task={} instance={} completed answerLength={}",
                    task.traceId(), taskId, instanceId, answer.length());

            List<A2AProtocol.TaskEvent> emitted = new java.util.ArrayList<>();
            emitted.add(new A2AProtocol.TaskEvent(taskId, A2AProtocol.TaskState.WORKING, ""));
            for (String piece : split(answer)) {
                emitted.add(new A2AProtocol.TaskEvent(taskId, A2AProtocol.TaskState.WORKING, piece));
            }
            emitted.add(new A2AProtocol.TaskEvent(taskId, A2AProtocol.TaskState.COMPLETED, ""));
            return Flux.fromIterable(emitted).delayElements(Duration.ofMillis(20));
        });
    }

    private static List<String> split(String answer) {
        List<String> pieces = new java.util.ArrayList<>();
        int step = 40;
        for (int i = 0; i < answer.length(); i += step) {
            pieces.add(answer.substring(i, Math.min(answer.length(), i + step)));
        }
        return pieces.isEmpty() ? List.of("") : pieces;
    }

    private String compose(String question, List<Map<String, String>> hits) {
        if (hits.isEmpty()) {
            return "资料里没有相关内容，我不能凭猜测回答。";
        }
        StringBuilder material = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            material.append('[').append(i + 1).append("] 来源=").append(hits.get(i).get("docName"))
                    .append('\n').append(hits.get(i).get("text")).append("\n\n");
        }
        String answer = chatModel.call(new Prompt(List.of(
                new SystemMessage("""
                        你是项目知识 Agent。只能依据给出的资料回答，并在句末用 [n] 标出来源。
                        资料里没有的不要补充，直接说「资料里没有相关内容」。回答简短。
                        """),
                new UserMessage("问题：" + question + "\n\n资料：\n" + material))))
                .getResult().getOutput().getText();
        return answer == null || answer.isBlank() ? "资料里没有相关内容，我不能凭猜测回答。" : answer;
    }

    /** 版本协商：不兼容就显式、快速地失败（409），绝不「尽力而为」。 */
    private static void requireCompatibleVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "缺少 " + A2AProtocol.VERSION_HEADER + " 头：协议版本必须显式声明");
        }
        if (!A2AProtocol.VERSION.equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "协议版本不兼容：本服务 " + A2AProtocol.VERSION + "，调用方 " + version);
        }
    }
}
