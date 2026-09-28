package com.example.digitalhuman.tools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 工具执行的线程池：让「超时掐断」有地方执行，也避免工具把对话线程占满。 */
@Configuration
public class ToolExecutionConfig {

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService toolExecutor(@Value("${digital-human.tools.pool-size:4}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "tool-exec");
            thread.setDaemon(true);
            return thread;
        });
    }
}
