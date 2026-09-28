package com.example.digitalhuman.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Agent 配置：与数字人项目一对一。
 *
 * <p>本掌只用 {@code systemPrompt}；{@code model} / {@code temperature} / {@code maxTokens}
 * 是冻结表结构的一部分，真正生效在第 4 掌（御模对话），本掌不假装它们已经生效。
 */
@Entity
@Table(name = "agent_config")
public class AgentConfig {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个专业的数字人主播，回答简洁友好。";
    public static final String DEFAULT_PROVIDER = "deepseek";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    /** provider 才是路由键：决定请求打到哪个 endpoint、用哪套鉴权头。 */
    @Column(name = "provider", nullable = false, length = 32)
    private String provider = DEFAULT_PROVIDER;

    @Column(name = "model", nullable = false, length = 64)
    private String model = "deepseek-flash";

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

    @Column(name = "temperature", nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature = new BigDecimal("0.70");

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 1024;

    protected AgentConfig() {
        // JPA 需要
    }

    public static AgentConfig forProject(Long projectId) {
        AgentConfig config = new AgentConfig();
        config.projectId = projectId;
        return config;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        if (provider != null && !provider.isBlank()) {
            this.provider = provider;
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        if (model != null && !model.isBlank()) {
            this.model = model;
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            this.systemPrompt = systemPrompt;
        }
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        if (temperature != null) {
            this.temperature = temperature;
        }
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        if (maxTokens > 0) {
            this.maxTokens = maxTokens;
        }
    }
}
