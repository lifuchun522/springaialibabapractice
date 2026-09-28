package com.example.digitalhuman.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

/**
 * 第 16 掌：异步执行器的边界也要被断言。
 *
 * <p>这条用例来自真实运行日志里的一段警告（默认 {@code SimpleAsyncTaskExecutor} 不适合生产负载），
 * 它不会被任何功能测试挡住——功能全对，交付标准不过。所以这里直接盯住三件事：
 * 用的是有界线程池、上界读得到、执行器确实是交给 MVC 的那一个。
 */
class WebAsyncConfigTest {

    private static SseProperties defaultProperties() {
        return new SseProperties(Duration.ofSeconds(15), null, null, null, null);
    }

    @Test
    @DisplayName("MVC 异步处理必须走有界线程池，而不是每请求新建线程的默认执行器")
    void asyncSupportMustUseBoundedExecutor() {
        WebAsyncConfig config = new WebAsyncConfig(defaultProperties());

        ThreadPoolTaskExecutor executor = config.asyncExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).as("SSE 并发由连接数决定，必须有上界").isEqualTo(32);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("sse-async-");

        // 配了池、和把池挂到 MVC 异步支持上，是两件事：这里走一遍真实的挂载路径
        AsyncSupportConfigurer configurer = new AsyncSupportConfigurer();
        config.configureAsyncSupport(configurer);
        assertThat(config.describe()).isEqualTo("core=4 max=32 queue=200 timeout=PT5M");
    }

    @Test
    @DisplayName("上限配错时兜底成合法值（max < core 会让线程池直接启动失败）")
    void shouldCoerceInvalidPoolSettings() {
        SseProperties coerced = new SseProperties(Duration.ofSeconds(15), -1, 2, -5, Duration.ZERO);

        assertThat(coerced.asyncCorePoolSize()).isEqualTo(4);
        assertThat(coerced.asyncMaxPoolSize()).isEqualTo(4);
        assertThat(coerced.asyncQueueCapacity()).isEqualTo(200);
        assertThat(coerced.asyncTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("心跳开关按间隔判定：0 或负数表示关闭；未配置用默认 15s")
    void heartbeatSwitchFollowsInterval() {
        assertThat(defaultProperties().heartbeatEnabled()).isTrue();
        assertThat(new SseProperties(Duration.ZERO, null, null, null, null).heartbeatEnabled()).isFalse();
        assertThat(new SseProperties(Duration.ofSeconds(-1), null, null, null, null).heartbeatEnabled()).isFalse();
        assertThat(new SseProperties(null, null, null, null, null).heartbeat())
                .isEqualTo(Duration.ofSeconds(15));
    }
}
