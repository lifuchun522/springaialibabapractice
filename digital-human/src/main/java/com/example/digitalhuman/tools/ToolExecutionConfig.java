package com.example.digitalhuman.tools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.digitalhuman.observability.ContextAwareExecutorService;

@Configuration
public class ToolExecutionConfig {

    /**
     * 工具执行池（第 6 掌）。
     *
     * <p>第 17 掌给它包了一层 {@link ContextAwareExecutorService}：工具在线程池里跑，
     * 而身份是 ThreadLocal —— 不传播的话，工具那一层的日志会丢掉 traceId/sessionId，
     * 于是「同一次请求」又变成要靠时间戳对表。
     *
     * <p>刻意**不在调用方**逐个记得「先捕获再恢复」：那种纪律一定会有人忘。
     * 身份传播是执行器的性质，不是调用者的自觉（与第 16 掌 profile 隔离同理）。
     */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService toolExecutor(@Value("${digital-human.tools.pool-size:4}") int poolSize) {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "tool-exec");
            thread.setDaemon(true);
            return thread;
        });
        return new ContextAwareExecutorService(pool);
    }
}