package com.example.digitalhuman.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.digitalhuman.domain.ToolCallAudit;
import com.example.digitalhuman.repository.ToolCallAuditRepository;

/**
 * 工具调用审计的读取用例。
 *
 * <p>第 16 掌补这一层的原因很具体：这条查询原来直接写在 {@code ToolController} 里——
 * Controller 自己注入 repository、自己查库。它跑得通，但把三件事混进了 HTTP 层：
 * 「谁能读」（授权）、「读哪个项目的」（归属校验）、「怎么读」（查询与排序）。
 * 结果就是其它入口（定时任务、运维接口、内网工具）想复用这段逻辑，只能再抄一遍。
 *
 * <p>依赖方向因此被拉平：<b>web → service → repository</b>。
 * 这也是 ArchUnit 门禁第一条真实拦下的东西（见 {@code LayeredArchitectureTest}）。
 */
@Service
public class ToolAuditService {

    private final ToolCallAuditRepository audits;
    private final ProjectService projectService;

    public ToolAuditService(ToolCallAuditRepository audits, ProjectService projectService) {
        this.audits = audits;
        this.projectService = projectService;
    }

    /** 读某个项目的工具调用审计；授权与归属校验是这条用例的一部分，不外留给调用方。 */
    @Transactional(readOnly = true)
    public List<ToolCallAudit> listForOwner(Long ownerId, Long projectId) {
        projectService.requireOwned(ownerId, projectId);
        return audits.findByProjectIdOrderByIdDesc(projectId);
    }
}
