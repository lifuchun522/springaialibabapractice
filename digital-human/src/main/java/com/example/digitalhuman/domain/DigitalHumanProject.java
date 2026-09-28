package com.example.digitalhuman.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 数字人项目：标题、主题色、背景图、开场白、结束语。
 *
 * <p>这些字段全都属于「配置」，运营改一次不该发一次版——所以它们落在库里，而不是代码或前端页面里。
 */
@Entity
@Table(name = "digital_human_project")
public class DigitalHumanProject {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "theme_color", nullable = false, length = 16)
    private String themeColor = "#2F6BFF";

    @Column(name = "background_url", length = 512)
    private String backgroundUrl;

    @Column(name = "opening_line", length = 512)
    private String openingLine;

    @Column(name = "closing_line", length = 512)
    private String closingLine;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_DRAFT;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DigitalHumanProject() {
        // JPA 需要
    }

    public DigitalHumanProject(Long ownerId, String name, String title) {
        this.ownerId = ownerId;
        this.name = name;
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getThemeColor() {
        return themeColor;
    }

    public void setThemeColor(String themeColor) {
        if (themeColor != null && !themeColor.isBlank()) {
            this.themeColor = themeColor;
        }
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
    }

    public String getOpeningLine() {
        return openingLine;
    }

    public void setOpeningLine(String openingLine) {
        this.openingLine = openingLine;
    }

    public String getClosingLine() {
        return closingLine;
    }

    public void setClosingLine(String closingLine) {
        this.closingLine = closingLine;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status != null && !status.isBlank()) {
            this.status = status;
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
