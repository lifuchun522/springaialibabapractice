package com.example.digitalhuman.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.digitalhuman.config.DigitalHumanChatProperties;
import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.repository.DigitalHumanProjectRepository;

/**
 * 数字人项目与它的 Agent 配置。
 *
 * <p>所有权校验放在服务层：任何按 id 的操作都必须带上 ownerId，避免「知道 id 就能改别人项目」。
 */
@Service
public class ProjectService {

    private final DigitalHumanProjectRepository projects;
    private final AgentConfigRepository agentConfigs;
    private final DigitalHumanChatProperties chatProperties;

    public ProjectService(DigitalHumanProjectRepository projects,
                          AgentConfigRepository agentConfigs,
                          DigitalHumanChatProperties chatProperties) {
        this.projects = projects;
        this.agentConfigs = agentConfigs;
        this.chatProperties = chatProperties;
    }

    /** 创建项目与其一对一 Agent 配置，配置默认值来自 application.yml 而不是硬编码。 */
    @Transactional
    public DigitalHumanProject create(Long ownerId, ProjectCommand command) {
        String name = requireText(command.name(), "项目名称不能为空");
        String title = requireText(command.title(), "项目标题不能为空");

        DigitalHumanProject project = new DigitalHumanProject(ownerId, name, title);
        project.setThemeColor(command.themeColor());
        project.setBackgroundUrl(command.backgroundUrl());
        project.setOpeningLine(command.openingLine());
        project.setClosingLine(command.closingLine());
        DigitalHumanProject saved = projects.save(project);

        AgentConfig config = AgentConfig.forProject(saved.getId());
        config.setSystemPrompt(command.systemPrompt() == null || command.systemPrompt().isBlank()
                ? chatProperties.defaultSystem()
                : command.systemPrompt());
        agentConfigs.save(config);

        return saved;
    }

    @Transactional
    public DigitalHumanProject update(Long ownerId, Long projectId, ProjectCommand command) {
        DigitalHumanProject project = requireOwned(ownerId, projectId);
        project.setName(requireText(command.name(), "项目名称不能为空"));
        project.setTitle(requireText(command.title(), "项目标题不能为空"));
        project.setThemeColor(command.themeColor());
        project.setBackgroundUrl(command.backgroundUrl());
        project.setOpeningLine(command.openingLine());
        project.setClosingLine(command.closingLine());
        project.setStatus(command.status());

        if (command.systemPrompt() != null && !command.systemPrompt().isBlank()) {
            agentConfigs.findByProjectId(projectId)
                    .ifPresent(config -> config.setSystemPrompt(command.systemPrompt()));
        }
        return projects.save(project);
    }

    @Transactional
    public void delete(Long ownerId, Long projectId) {
        DigitalHumanProject project = requireOwned(ownerId, projectId);
        agentConfigs.findByProjectId(projectId).ifPresent(agentConfigs::delete);
        projects.delete(project);
    }

    @Transactional(readOnly = true)
    public List<DigitalHumanProject> list(Long ownerId) {
        return projects.findByOwnerIdOrderByIdDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public DigitalHumanProject requireOwned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在：" + projectId));
    }

    /** 运行页要的完整配置：项目本身 + 它的 Agent 配置。 */
    @Transactional(readOnly = true)
    public RuntimeView runtimeView(Long projectId) {
        DigitalHumanProject project = projects.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在：" + projectId));
        AgentConfig config = agentConfigs.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目缺少 Agent 配置：" + projectId));
        return new RuntimeView(project, config);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public record ProjectCommand(String name,
                                 String title,
                                 String themeColor,
                                 String backgroundUrl,
                                 String openingLine,
                                 String closingLine,
                                 String status,
                                 String systemPrompt) {
    }

    public record RuntimeView(DigitalHumanProject project, AgentConfig agentConfig) {
    }
}
