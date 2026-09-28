package com.example.digitalhuman.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.ProjectService;
import com.example.digitalhuman.service.TitleChangeService;

/** 数字人项目 CRUD 与运行页配置读取。 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final AuthService authService;
    private final ProjectService projectService;
    private final TitleChangeService titleChangeService;

    public ProjectController(AuthService authService, ProjectService projectService,
                             TitleChangeService titleChangeService) {
        this.authService = authService;
        this.projectService = projectService;
        this.titleChangeService = titleChangeService;
    }

    public record ProjectRequest(String name,
                                 String title,
                                 String themeColor,
                                 String backgroundUrl,
                                 String openingLine,
                                 String closingLine,
                                 String status,
                                 String systemPrompt) {

        ProjectService.ProjectCommand toCommand() {
            return new ProjectService.ProjectCommand(name, title, themeColor, backgroundUrl,
                    openingLine, closingLine, status, systemPrompt);
        }
    }

    public record ProjectResponse(Long id,
                                  String name,
                                  String title,
                                  String themeColor,
                                  String backgroundUrl,
                                  String openingLine,
                                  String closingLine,
                                  String status,
                                  LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {

        static ProjectResponse of(DigitalHumanProject project) {
            return new ProjectResponse(project.getId(), project.getName(), project.getTitle(),
                    project.getThemeColor(), project.getBackgroundUrl(), project.getOpeningLine(),
                    project.getClosingLine(), project.getStatus(), project.getCreatedAt(),
                    project.getUpdatedAt());
        }
    }

    public record AgentConfigResponse(Long projectId,
                                      String model,
                                      String systemPrompt,
                                      java.math.BigDecimal temperature,
                                      int maxTokens) {

        static AgentConfigResponse of(AgentConfig config) {
            return new AgentConfigResponse(config.getProjectId(), config.getModel(),
                    config.getSystemPrompt(), config.getTemperature(), config.getMaxTokens());
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader("X-Token") String token,
                                      @RequestBody ProjectRequest request) {
        Long ownerId = authService.requireUserId(token);
        DigitalHumanProject project = projectService.create(ownerId, request.toCommand());
        return Map.of("id", project.getId());
    }

    @GetMapping
    public List<ProjectResponse> list(@RequestHeader("X-Token") String token) {
        Long ownerId = authService.requireUserId(token);
        return projectService.list(ownerId).stream().map(ProjectResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse detail(@RequestHeader("X-Token") String token, @PathVariable Long id) {
        Long ownerId = authService.requireUserId(token);
        return ProjectResponse.of(projectService.requireOwned(ownerId, id));
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@RequestHeader("X-Token") String token,
                                  @PathVariable Long id,
                                  @RequestBody ProjectRequest request) {
        Long ownerId = authService.requireUserId(token);
        return ProjectResponse.of(projectService.update(ownerId, id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Token") String token, @PathVariable Long id) {
        Long ownerId = authService.requireUserId(token);
        projectService.delete(ownerId, id);
    }

    /** 运行页读取的完整配置；不需要登录，运行页是给终端用户看的。 */
    @GetMapping("/{id}/runtime")
    public Map<String, Object> runtime(@PathVariable Long id) {
        ProjectService.RuntimeView view = projectService.runtimeView(id);
        return Map.of(
                "project", ProjectResponse.of(view.project()),
                "agent", AgentConfigResponse.of(view.agentConfig()));
    }

    public record AgentRequest(String provider,
                               String model,
                               String systemPrompt,
                               java.math.BigDecimal temperature,
                               Integer maxTokens) {
    }

    /** 人类确认入口：把「待确认变更」真正落库。令牌单次有效，重复使用会被拒绝。 */
    @PostMapping("/{id}/pending-changes/{token}/confirm")
    public ProjectResponse confirmTitleChange(@RequestHeader("X-Token") String token,
                                              @PathVariable Long id,
                                              @PathVariable("token") String confirmToken) {
        Long ownerId = authService.requireUserId(token);
        return ProjectResponse.of(titleChangeService.confirm(id, ownerId, confirmToken));
    }

    /** 改人设与模型：只动数据，不动代码，也不重启服务。 */
    @PutMapping("/{id}/agent")
    public AgentConfigResponse updateAgent(@RequestHeader("X-Token") String token,
                                           @PathVariable Long id,
                                           @RequestBody AgentRequest request) {
        Long ownerId = authService.requireUserId(token);
        return AgentConfigResponse.of(projectService.updateAgent(ownerId, id,
                new ProjectService.AgentCommand(request.provider(), request.model(),
                        request.systemPrompt(), request.temperature(), request.maxTokens())));
    }
}
