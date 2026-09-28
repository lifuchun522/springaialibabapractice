package com.example.digitalhuman.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.domain.PendingTitleChange;
import com.example.digitalhuman.repository.DigitalHumanProjectRepository;
import com.example.digitalhuman.repository.PendingTitleChangeRepository;

/**
 * 改标题的「待确认 → 确认」通道。
 *
 * <p>设计要点：**写操作不经过模型的手**。模型只能提出「待确认变更」并拿到一个令牌，
 * 真正的落库由人类确认这一条确定性路径完成。这样即使模型被提示词注入带着跑，
 * 它能造成的最大后果也只是多了一条待确认记录。
 *
 * <p>令牌单次有效：重复确认直接拒绝，不会把同一次变更应用两遍。
 */
@Service
public class TitleChangeService {

    private final DigitalHumanProjectRepository projects;
    private final PendingTitleChangeRepository pendingChanges;

    public TitleChangeService(DigitalHumanProjectRepository projects,
                              PendingTitleChangeRepository pendingChanges) {
        this.projects = projects;
        this.pendingChanges = pendingChanges;
    }

    /** 只落一条待确认记录，不动业务数据。 */
    @Transactional
    public PendingTitleChange propose(Long projectId, Long ownerId, String newTitle) {
        if (newTitle == null || newTitle.isBlank() || newTitle.length() > 60) {
            throw new IllegalArgumentException("新标题必须是 1~60 个字符");
        }
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return pendingChanges.save(new PendingTitleChange(projectId, ownerId, newTitle.trim(), token));
    }

    /** 人类确认：应用变更并消费令牌；令牌重复使用会被拒绝。 */
    @Transactional
    public DigitalHumanProject confirm(Long projectId, Long ownerId, String confirmToken) {
        PendingTitleChange change = pendingChanges.findByConfirmToken(confirmToken)
                .orElseThrow(() -> new ConfirmationRejectedException("确认令牌不存在：" + confirmToken));

        if (!change.getProjectId().equals(projectId) || !change.getOwnerId().equals(ownerId)) {
            throw new ConfirmationRejectedException("确认令牌不属于该项目或该账号");
        }
        if (change.getStatus() != PendingTitleChange.Status.PENDING) {
            throw new ConfirmationRejectedException("确认令牌已使用：" + confirmToken);
        }

        DigitalHumanProject project = projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在：" + projectId));

        change.markResolved(PendingTitleChange.Status.CONFIRMED);
        pendingChanges.save(change);

        project.setTitle(change.getNewTitle());
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public List<PendingTitleChange> pendingOf(Long projectId) {
        return pendingChanges.findByProjectIdAndStatusOrderByIdDesc(projectId, PendingTitleChange.Status.PENDING);
    }
}
