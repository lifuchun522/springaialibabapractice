package com.example.digitalhuman.config;

import java.time.Duration;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 给 SSE 流转接上**周期性心跳**（第 16 掌的「SSE 契约化」）。
 *
 * <p>心跳帧用注释行（{@code :heartbeat}）而不是数据帧：注释行按 SSE 规范会被客户端忽略，
 * 因此它只干一件事——让链路上每一层都看到「这条连接有数据在动」，不会被空闲超时掐掉。
 * 用数据帧当心跳会更糟：前端得专门判断「这帧是不是真的内容」，判断漏了就在回答里多出一个字。
 *
 * <p>两个实现上的坑，都是踩过才知道的：
 * <ol>
 *   <li><b>心跳必须能被终止。</b>直觉写法是 {@code Flux.merge(数据流, 心跳流)}，
 *       而 {@code Flux.interval} 永远不会自然结束——数据流早就答完了，合并后的流还开着，
 *       客户端收到的表现是「内容结束但连接不关」，超时后报错。所以这里用 {@code doFinally}
 *       显式释放定时器并完成心跳流。</li>
 *   <li><b>心跳不能攒。</b>用 {@code Sinks.many().multicast().onBackpressureBuffer()}：
 *       没有订阅者时不留缓存，避免客户端中途断开后把旧心跳补发出去。</li>
 * </ol>
 *
 * <p>放在 config 包（基础设施侧）而不是 Controller 里：Controller 只该声明
 * 「这个端点是 {@code text/event-stream}」，至于怎么维持这条连接，是传输层的事。
 */
@Component
public class SseHeartbeat {

    /** 心跳帧的正文；用注释而不是数据，客户端按规范直接忽略。 */
    public static final String HEARTBEAT_COMMENT = "heartbeat";

    private final SseProperties properties;

    public SseHeartbeat(SseProperties properties) {
        this.properties = properties;
    }

    /**
     * @param source 真实数据流（token / 事件）
     * @return 接上心跳后的流；心跳关闭时原样返回
     */
    public <T> Flux<ServerSentEvent<T>> attach(Flux<ServerSentEvent<T>> source) {
        if (!properties.heartbeatEnabled()) {
            return source;
        }

        Duration interval = properties.heartbeat();
        return Flux.defer(() -> {
            // done 是「源流已结束」这条事实的**可重放**信号：
            // Sinks.empty() 会把终止信号留给之后的订阅者，所以即使源流瞬间就结束了
            // （例如本次响应只有一段、或者上游立刻失败），心跳流也立刻随之结束，
            // 不会出现「数据完了、心跳还在响」的窗口。
            Sinks.Empty<Void> done = Sinks.empty();

            // 源只被订阅一次：它背后是一次真实的模型调用，订阅两次就是花两次钱、生成两段答案。
            Flux<ServerSentEvent<T>> data = source.doFinally(signal -> done.tryEmitEmpty());
            Flux<ServerSentEvent<T>> beats = Flux.interval(interval)
                    .map(tick -> ServerSentEvent.<T>builder().comment(HEARTBEAT_COMMENT).build())
                    .takeUntilOther(done.asMono());

            return Flux.merge(data, beats);
        });
    }
}
