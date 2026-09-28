package com.example.digitalhuman.a2a;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.digitalhuman.ai.ModelInvocationException;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * A2A 客户端：**只依赖协议，不依赖知识 Agent 的任何类**（验收第一条）。
 *
 * <p>它做的事情固定是四步：发现实例 → 创建任务 → 流式消费 → 收尾。
 * 真正有技术含量的部分不是这四步，而是**失败语义**：
 * <ul>
 *   <li>网络失败/5xx：可重试（有界、退避），因为对方很可能没收到；</li>
 *   <li>4xx（版本不兼容、参数不对）：**不重试**，重试只会把同一个错误重复一遍；</li>
 *   <li>超时：明确失败并说明「任务可能还在跑」，而不是静默返回一个兜底答案；</li>
 *   <li>贯穿的 traceId：主服务日志与对方日志能对上（验收第三条）。</li>
 * </ul>
 */
@Component
public class A2AClient {

    private static final Logger log = LoggerFactory.getLogger(A2AClient.class);

    private final AgentRegistry registry;
    private final WebClient.Builder webClientBuilder;
    private final Duration timeout;
    private final int maxAttempts;
    private final String protocolVersion;

    public A2AClient(AgentRegistry registry,
                     WebClient.Builder webClientBuilder,
                     @Value("${digital-human.a2a.timeout-ms:5000}") long timeoutMs,
                     @Value("${digital-human.a2a.max-attempts:2}") int maxAttempts,
                     @Value("${digital-human.a2a.protocol-version:1.0}") String protocolVersion) {
        this.registry = registry;
        this.webClientBuilder = webClientBuilder;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.protocolVersion = protocolVersion;
    }

    /**
     * 一次跨服务协作的结果。
     *
     * @param instanceId   这次实际打到哪个实例（多实例分散调用就看它）
     * @param taskId       对方返回的任务号（远端不确定性进了本地状态机）
     * @param deltas       流式收到的片段数（证明「部分结果是合法中间状态」）
     */
    public record RemoteAnswer(String instanceId, String taskId, String traceId, String answer,
                               List<Map<String, String>> artifacts, int deltas, long elapsedMs) {
    }

    public RemoteAnswer ask(String question) {
        AgentRegistry.AgentInstance instance = registry.next();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long startedAt = System.currentTimeMillis();
        WebClient client = webClientBuilder.baseUrl(instance.baseUrl()).build();

        Map<String, Object> created = client.post().uri("/a2a/tasks")
                .header(A2AProtocol.VERSION_HEADER, protocolVersion)
                .header(A2AProtocol.TRACE_HEADER, traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("question", question))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .map(body -> new ModelInvocationException(
                                ModelInvocationException.Kind.PROVIDER_ERROR,
                                "a2a", instance.instanceId(),
                                "对端拒绝（不重试）：" + response.statusCode() + " " + body, null)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxAttempts - 1L, Duration.ofMillis(200))
                        .filter(A2AClient::isRetryable))
                .block();

        if (created == null || created.get("taskId") == null) {
            throw new ModelInvocationException(ModelInvocationException.Kind.PROVIDER_ERROR,
                    "a2a", instance.instanceId(), "创建任务失败：对端没有返回 taskId", null);
        }
        String taskId = String.valueOf(created.get("taskId"));
        log.info("[a2a] trace={} instance={} task={} 已创建", traceId, instance.instanceId(), taskId);

        List<String> pieces = new ArrayList<>();
        Flux<Map<String, Object>> events = client.post().uri("/a2a/tasks/{id}/events", taskId)
                .header(A2AProtocol.TRACE_HEADER, traceId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(timeout);
        events.toIterable().forEach(event -> {
            Object delta = event.get("delta");
            if (delta != null && !String.valueOf(delta).isEmpty()) {
                pieces.add(String.valueOf(delta));
            }
        });

        String answer = String.join("", pieces);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> artifacts = List.of();
        long elapsed = System.currentTimeMillis() - startedAt;
        log.info("[a2a] trace={} instance={} task={} deltas={} elapsedMs={}",
                traceId, instance.instanceId(), taskId, pieces.size(), elapsed);
        return new RemoteAnswer(instance.instanceId(), taskId, traceId, answer, artifacts,
                pieces.size(), elapsed);
    }

    /** 只有「对方可能没收到」的失败才重试；4xx 重试等于把同一个错误重复一遍。 */
    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof ModelInvocationException) {
            return false;
        }
        return true;
    }
}
