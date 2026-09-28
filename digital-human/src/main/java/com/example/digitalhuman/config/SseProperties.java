package com.example.digitalhuman.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 传输层配置（第 16 掌）。
 *
 * <p>心跳不是「优化项」，是契约的一部分：SSE 是长连接 + 小包，模型思考或工具执行期间
 * 可能几十秒没有下行数据，任何一层网关的空闲超时都会把这种「静默」判成死连接。
 * 文章 06 链 B 的现象——直连 {@code curl -N} 正常、经网关后长时间空白或 60 秒断开——
 * 就是这条链路上最典型的故障，而它**不是模型问题**，所以也不能靠改模型参数解决。
 *
 * @param heartbeat 心跳间隔；设为 {@code 0} 或负数表示关闭心跳（内网直连调试时可用）
 * @param asyncCorePoolSize 异步（SSE）执行器核心线程数
 * @param asyncMaxPoolSize 异步执行器最大线程数：SSE 的并发由**连接数**决定，所以必须有上界
 * @param asyncQueueCapacity 异步执行器队列容量
 * @param asyncTimeout 单条流式请求的最长占用时间；到点由容器结束异步处理并关闭连接
 */
@ConfigurationProperties(prefix = "digital-human.sse")
public record SseProperties(Duration heartbeat,
                            Integer asyncCorePoolSize,
                            Integer asyncMaxPoolSize,
                            Integer asyncQueueCapacity,
                            Duration asyncTimeout) {

    /** 默认 15 秒：小于常见网关的 30/60 秒空闲阈值，又不会把连接打成噪声。 */
    private static final Duration DEFAULT_HEARTBEAT = Duration.ofSeconds(15);

    public SseProperties {
        if (heartbeat == null) {
            heartbeat = DEFAULT_HEARTBEAT;
        }
        asyncCorePoolSize = asyncCorePoolSize == null || asyncCorePoolSize <= 0 ? 4 : asyncCorePoolSize;
        asyncMaxPoolSize = asyncMaxPoolSize == null || asyncMaxPoolSize <= 0 ? 32 : asyncMaxPoolSize;
        asyncQueueCapacity = asyncQueueCapacity == null || asyncQueueCapacity < 0 ? 200 : asyncQueueCapacity;
        if (asyncMaxPoolSize < asyncCorePoolSize) {
            asyncMaxPoolSize = asyncCorePoolSize;
        }
        asyncTimeout = asyncTimeout == null || asyncTimeout.isZero() || asyncTimeout.isNegative()
                ? Duration.ofMinutes(5) : asyncTimeout;
    }

    public boolean heartbeatEnabled() {
        return heartbeat != null && !heartbeat.isZero() && !heartbeat.isNegative();
    }
}
