package com.example.digitalhuman.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.digitalhuman.domain.PendingTitleChange;
import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.ToolCallAuditRepository;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ProjectService;
import com.example.digitalhuman.service.TitleChangeService;

/** 工具调用审计与待确认变更的查询入口（都属于运营可查的事实，不是模型能编的内容）。 */
@RestController
@RequestMapping("/api/projects/{id}")
public class ToolController {

    private final AuthService authService;
    private final ProjectService projectService;
    private final TitleChangeService titleChangeService;
    private final ToolCallAuditRepository audits;

    public ToolController(AuthService authService,
                          ProjectService projectService,
                          TitleChangeService titleChangeService,
                          ToolCallAuditRepository audits) {
        this.authService = authService;
        this.projectService = projectService;
        this.titleChangeService = titleChangeService;
        this.audits = audits;
    }

    public record ToolAuditView(Long id, String toolName, String arguments, String resultSummary,
                                String status, long elapsedMs, String traceId, String sessionId,
                                String createdAt) {

        static ToolAuditView of(ToolCallAudit audit) {
            return new ToolAuditView(audit.getId(), audit.getToolName(), audit.getArguments(),
                    audit.getResultSummary(), audit.getStatus().name(), audit.getElapsedMs(),
                    audit.getTraceId(), audit.getSessionId(), String.valueOf(audit.getCreatedAt()));
        }
    }

    public record PendingChangeView(Long id, String newTitle, String confirmToken, String status, String createdAt) {

        static PendingChangeView of(PendingTitleChange change) {
            return new PendingChangeView(change.getId(), change.getNewTitle(), change.getConfirmToken(),
                    change.getStatus().name(), String.valueOf(change.getCreatedAt()));
        }
    }

    /** 每一次工具调用都留痕：谁、哪个工具、什么参数、结果、耗时、追踪号。 */
    @GetMapping("/tool-audits")
    public List<ToolAuditView> toolAudits(@RequestHeader("X-Token") String token, @PathVariable Long id) {
        Long ownerId = authService.requireUserId(token);
        projectService.requireOwned(ownerId, id);
        return audits.findByProjectIdOrderByIdDesc(id).stream().map(ToolAuditView::of).toList();
    }

    /** 待确认的写变更：还没生效，等人确认。 */
    @GetMapping("/pending-changes")
    public List<PendingChangeView> pendingChanges(@RequestHeader("X-Token") String token, @PathVariable Long id) {
        Long ownerId = authService.requireUserId(token);
        projectService.requireOwned(ownerId, id);
        return titleChangeService.pendingOf(id).stream().map(PendingChangeView::of).toList();
    }
}
