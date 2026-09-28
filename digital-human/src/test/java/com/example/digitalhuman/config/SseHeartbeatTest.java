package com.example.digitalhuman.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 第 16 掌：心跳是契约，不是优化项——所以它也要被断言。
 *
 * <p>三条断言分别对应三个真实会出事的点：
 * <ol>
 *   <li>静默期必须有帧下行（否则网关把连接判成空闲）；</li>
 *   <li>数据流结束后**必须结束**（否则客户端「内容完了但不关连接」，只能等超时）；</li>
 *   <li>心跳帧必须是注释、数据帧必须原样——把心跳写成数据帧，前端就会在回答里多出一个字。</li>
 * </ol>
 */
class SseHeartbeatTest {

    private static final ServerSentEvent<String> DATA = ServerSentEvent.builder("第一段").build();

    @Test
    @DisplayName("静默期仍有心跳帧，且数据帧原样通过")
    void shouldEmitHeartbeatDuringSilence() {
        SseHeartbeat heartbeat = new SseHeartbeat(new SseProperties(Duration.ofMillis(40), null, null, null, null));

        // 数据在 10ms 后到，之后静默 130ms：没有心跳时这段静默在链路上就是「死连接」
        Flux<ServerSentEvent<String>> source = Flux.concat(
                Flux.just(DATA).delayElements(Duration.ofMillis(10)),
                Flux.<ServerSentEvent<String>>never().take(Duration.ofMillis(130)));

        List<ServerSentEvent<String>> events = heartbeat.attach(source)
                .takeUntil(event -> event.data() != null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).isNotNull();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("第一段");

        long beats = heartbeat.attach(Flux.concat(
                        Flux.just(DATA).delayElements(Duration.ofMillis(10)),
                        Flux.<ServerSentEvent<String>>never().take(Duration.ofMillis(130))))
                .take(Duration.ofMillis(120))
                .filter(event -> SseHeartbeat.HEARTBEAT_COMMENT.equals(event.comment()))
                .count()
                .block(Duration.ofSeconds(5));

        assertThat(beats).as("静默 130ms、心跳 40ms：应该看到 2~3 个心跳帧").isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("数据流结束后整个流必须结束：不能让客户端等到超时")
    void shouldCompleteWhenSourceCompletes() {
        SseHeartbeat heartbeat = new SseHeartbeat(new SseProperties(Duration.ofMillis(20), null, null, null, null));

        StepVerifier.create(heartbeat.attach(Flux.just(DATA)))
                .assertNext(event -> assertThat(event.data()).isEqualTo("第一段"))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("心跳可关闭：内网直连调试时不该被迫看心跳")
    void shouldAllowDisablingHeartbeat() {
        SseHeartbeat disabled = new SseHeartbeat(new SseProperties(Duration.ZERO, null, null, null, null));

        StepVerifier.create(disabled.attach(Flux.just(DATA)))
                .assertNext(event -> assertThat(event.comment()).isNull())
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
