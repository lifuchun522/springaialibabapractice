package com.example.digitalhuman.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SSE 的异步执行器（第 16 掌真实验收抓到的第三个问题）。
 *
 * <p>第一次带着心跳跑真实流式请求时，日志里出现了这样一段警告：
 * <pre>
 * Performing asynchronous handling through the default Spring MVC SimpleAsyncTaskExecutor.
 * This executor is not suitable for production use under load.
 * Please, configure an AsyncTaskExecutor through the WebMvc config.
 * </pre>
 * 它没有被任何单测挡住，因为单测不压异步容器；它也不会在功能上出错，只会**在负载下**出错——
 * 每个请求新建一个线程、没有上限、没有队列。这类问题正是文章说的「Demo 能跑、交付不能」：
 * 功能验收全过，交付标准不过。
 *
 * <p>两条边界写在这里：
 * <ul>
 *   <li><b>池子必须有界</b>：SSE 是长连接，并发数由连接数决定，而不是由 QPS 决定；
 *       不设上限时，连接一多线程就爆，最后连健康检查都排不上队。</li>
 *   <li><b>超时必须显式</b>：没有超时的长连接会一直占着工作线程；超时到点后
 *       Spring 会结束异步处理并关闭连接，客户端可以重连。</li>
 * </ul>
 */
@Configuration
public class WebAsyncConfig implements WebMvcConfigurer {

    private final SseProperties properties;
    private final ThreadPoolTaskExecutor asyncExecutor;

    public WebAsyncConfig(SseProperties properties) {
        this.properties = properties;
        this.asyncExecutor = buildExecutor(properties);
    }

    private static ThreadPoolTaskExecutor buildExecutor(SseProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.asyncCorePoolSize());
        executor.setMaxPoolSize(properties.asyncMaxPoolSize());
        executor.setQueueCapacity(properties.asyncQueueCapacity());
        executor.setThreadNamePrefix("sse-async-");
        // 第 17 掌：SSE 由异步执行器托管，身份必须跟着任务一起过来，否则流式那一半日志没有 traceId
        executor.setTaskDecorator(new com.example.digitalhuman.observability.ContextAwareTaskDecorator());
        // 优雅停机：发版时不要在半句话上把连接掐断
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(asyncExecutor);
        configurer.setDefaultTimeout(properties.asyncTimeout().toMillis());
    }

    /**
     * 暴露给测试与运维文档引用：**就是**交给 MVC 的那个执行器，不是「另建一个长得像的」。
     * 断言配了一个有界池、和执行器真的被挂上去，是两件事，不能只验前者。
     */
    public ThreadPoolTaskExecutor asyncExecutor() {
        return asyncExecutor;
    }

    /** 当前生效的异步配置摘要。 */
    public String describe() {
        return "core=%d max=%d queue=%d timeout=%s"
                .formatted(properties.asyncCorePoolSize(), properties.asyncMaxPoolSize(),
                        properties.asyncQueueCapacity(), properties.asyncTimeout());
    }
}
