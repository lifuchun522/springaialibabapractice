package com.example.digitalhuman.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 启动期校验：把 {@link DeploymentContract} 挂在「配置已解析、Bean 还没创建」这一刻。
 *
 * <p>为什么选 {@code ApplicationContextInitializer} 而不是 {@code ApplicationRunner} 或 {@code @PostConstruct}：
 * 后两者都在 Bean 创建**之后**才跑，而 Spring AI 的 OpenAI 自动配置在创建 {@code OpenAiApi} 时
 * 自己就会因为空密钥抛异常（实测：{@code OpenAI API key must be set.}）。
 * 也就是说，用后两者时用户看到的永远是框架的报错，我们那条「缺哪个环境变量、去哪儿声明」的
 * 部署契约提示根本没机会出现——**校验的位置决定了失败信息归谁解释**。
 *
 * <p>注册点只有一个：{@code DigitalHumanApplication#main}。
 * 刻意不注册成 Bean，也不写进 {@code spring.factories}：
 * 单元测试与 {SpringBootTest} 不经过 {@code main}，因此不会被这条校验误伤
 * （测试环境本来就没有真实密钥，这是设计而不是巧合）。
 */
public class DeploymentContractInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        DeploymentContract.validate(applicationContext.getEnvironment());
    }
}
