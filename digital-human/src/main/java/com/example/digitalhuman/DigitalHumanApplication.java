package com.example.digitalhuman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.example.digitalhuman.config.DeploymentContractInitializer;

/**
 * 数字人项目最小骨架入口。
 *
 * <p>这一掌只交付一件事：把 ChatClient 做成模型调用的唯一出口，
 * 让后面的记忆、工具、RAG、Agent、Graph 都只在这一个位置生长。
 *
 * <p>第 16 掌补上「可交付」的那一半：启动前先过一次**部署契约**校验
 * （{@link DeploymentContractInitializer}）。它只在 {@code main} 里注册，
 * 因此单元测试与 {@code @SpringBootTest} 不会经过它——测试环境本来就没有真实密钥，
 * 这是设计而不是巧合。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DigitalHumanApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DigitalHumanApplication.class);
        application.addInitializers(new DeploymentContractInitializer());
        application.run(args);
    }
}
