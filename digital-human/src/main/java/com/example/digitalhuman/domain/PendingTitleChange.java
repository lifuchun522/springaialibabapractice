package com.example.digitalhuman.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 待确认的标题变更。
 *
 * <p>写操作的「人类确认」这一环不能靠模型自觉：
 * 模型对「讨论」和「指令」的区分本来就是概率性的，所以确认必须落在确定的代码里——
 * 没有确认令牌，就不产生任何业务数据变更，只留一条待确认记录。
 */
@Entity
@Table(name = "pending_title_change")
public class PendingTitleChange {

    public enum Status {
        PENDING, CONFIRMED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "new_title", nullable = false, length = 128)
    private String newTitle;

    @Column(name = "confirm_token", nullable = false, length = 64, unique = true)
    private String confirmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected PendingTitleChange() {
        // JPA 需要
    }

    public PendingTitleChange(Long projectId, Long ownerId, String newTitle, String confirmToken) {
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.newTitle = newTitle;
        this.confirmToken = confirmToken;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void markResolved(Status resolved) {
        this.status = resolved;
        this.resolvedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getNewTitle() {
        return newTitle;
    }

    public String getConfirmToken() {
        return confirmToken;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
