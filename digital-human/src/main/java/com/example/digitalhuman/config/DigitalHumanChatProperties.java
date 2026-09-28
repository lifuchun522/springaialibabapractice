package com.example.digitalhuman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数字人对话配置。
 *
 * <p>System Prompt 属于「配置」而不是「代码」：同一份代码要能服务多个数字人项目，
 * 运营改开场人设不该发一次版。
 *
 * @param defaultSystem 默认 System Prompt，配置缺失时回落到内置默认值
 */
@ConfigurationProperties(prefix = "digital-human.chat")
public record DigitalHumanChatProperties(String defaultSystem) {

    private static final String FALLBACK_SYSTEM = "你是一个数字人助手，回答简短、口语化。";

    public DigitalHumanChatProperties {
        if (defaultSystem == null || defaultSystem.isBlank()) {
            defaultSystem = FALLBACK_SYSTEM;
        }
    }
}
