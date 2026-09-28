package com.example.digitalhuman.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.workflow.DigitalHumanWorkflowService;

/**
 * 编排层入口。
 *
 * <p>对外仍然是「一句话进、一个字符串出」，与前九掌的运行页契约一致；
 * 多出来的是 {@code branch} 与 {@code nodes}：这次走了哪条分支、经过了哪些节点、各自多久。
 *
 * <p>四种模式各给一个端点，不是为了好看，是为了**验收**：
 * 「顺序模式连续二十次节点序列一致」这类断言必须能单独打到某一种编排上，
 * 混在一个入口里就没法证明是哪一种在守规矩。
 */
@RestController
@RequestMapping("/api/projects/{id}/workflow")
public class WorkflowController {

    private final DigitalHumanWorkflowService workflowService;

    public WorkflowController(DigitalHumanWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public record WorkflowRequest(String question, String sessionId) {
    }

    /** 售前 / 售后 / 兜底 路由：缺省入口，与「数字人该走哪条流程」这句话对应。 */
    @PostMapping("/ask")
    public DigitalHumanWorkflowService.WorkflowAnswer ask(@PathVariable("id") Long projectId,
                                                          @RequestBody WorkflowRequest request) {
        return workflowService.routed(projectId, sessionId(request), question(request));
    }

    @PostMapping("/sequential")
    public DigitalHumanWorkflowService.WorkflowAnswer sequential(@PathVariable("id") Long projectId,
                                                                 @RequestBody WorkflowRequest request) {
        return workflowService.sequential(projectId, sessionId(request), question(request));
    }

    @PostMapping("/parallel")
    public DigitalHumanWorkflowService.WorkflowAnswer parallel(@PathVariable("id") Long projectId,
                                                               @RequestBody WorkflowRequest request) {
        return workflowService.parallel(projectId, sessionId(request), question(request));
    }

    @PostMapping("/loop")
    public DigitalHumanWorkflowService.WorkflowAnswer loop(@PathVariable("id") Long projectId,
                                                           @RequestBody WorkflowRequest request) {
        return workflowService.loop(projectId, sessionId(request), question(request));
    }

    private static String question(WorkflowRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question 不能为空");
        }
        return request.question();
    }

    private static String sessionId(WorkflowRequest request) {
        return request == null ? null : request.sessionId();
    }
}
