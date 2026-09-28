package com.example.digitalhuman.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 账号。密码只存 BCrypt 哈希，永不存明文。 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 64, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
        // JPA 需要
    }

    public User(String username, String passwordHash, String displayName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
