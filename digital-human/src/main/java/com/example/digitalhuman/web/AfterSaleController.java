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

import com.example.digitalhuman.graph.AfterSaleGraphService;
import com.example.digitalhuman.graph.MysqlCheckpointSaver;

/**
 * 售后流程的状态图入口。
 *
 * <p>三个端点对应三条验收线：
 * <ul>
 *   <li>{@code POST .../run}：进入流程。高风险会在人工确认前**中断**并把控制权交回来（不阻塞线程）；</li>
 *   <li>{@code POST .../{threadId}/resume}：人工决定之后，用**同一个 threadId** 从断点继续；</li>
 *   <li>{@code GET .../{threadId}}：这条工单现在卡在哪一步——直接读检查点。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projects/{id}/after-sale")
public class AfterSaleController {

    private final AfterSaleGraphService graphService;

    public AfterSaleController(AfterSaleGraphService graphService) {
        this.graphService = graphService;
    }

    public record RunRequest(String question, String sessionId, String threadId) {
    }

    public record ResumeRequest(String decision, String sessionId) {
    }

    @PostMapping("/run")
    public AfterSaleGraphService.GraphRun run(@PathVariable("id") Long projectId,
                                              @RequestBody RunRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }
        return graphService.run(projectId, request.sessionId(), request.question(), request.threadId());
    }

    @PostMapping("/{threadId}/resume")
    public AfterSaleGraphService.GraphRun resume(@PathVariable("id") Long projectId,
                                                 @PathVariable("threadId") String threadId,
                                                 @RequestBody(required = false) ResumeRequest request) {
        String decision = request == null ? "APPROVE" : request.decision();
        String sessionId = request == null ? null : request.sessionId();
        return graphService.resume(projectId, sessionId, threadId, decision);
    }

    /** 断点历史：每个检查点对应「执行到哪个节点、下一个是谁、什么时候落的盘」。 */
    @GetMapping("/{threadId}")
    public Map<String, Object> state(@PathVariable("id") Long projectId,
                                     @PathVariable("threadId") String threadId) {
        List<MysqlCheckpointSaver.CheckpointRecord> records = graphService.history(threadId);
        return Map.of("threadId", threadId, "checkpoints", records, "checkpointCount", records.size());
    }

    /** 图结构导出（Mermaid）：流程不用读代码就能看懂，而且它跟着边一起变。 */
    @GetMapping("/graph")
    public Map<String, Object> graph(@PathVariable("id") Long projectId) {
        return Map.of("mermaid", graphService.asMermaid(projectId, "preview"));
    }
}
