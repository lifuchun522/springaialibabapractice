package com.example.digitalhuman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 数字人项目最小骨架入口。
 *
 * <p>这一掌只交付一件事：把 ChatClient 做成模型调用的唯一出口，
 * 让后面的记忆、工具、RAG、Agent、Graph 都只在这一个位置生长。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DigitalHumanApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalHumanApplication.class, args);
    }
}
