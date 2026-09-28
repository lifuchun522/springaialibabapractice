package com.example.digitalhuman.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * 统一打点入口（第 17 掌）。
 *
 * <p>文章的第二个反直觉判断值得抄下来：**最可靠的打点位置不在业务代码里，而在框架的 Hook 与
 * Observation 层**。业务代码会改、会被绕过、会被复制到另一个分支；而「调用模型」「执行工具」
 * 「推进图节点」是必经之路。站在必经之路上打点，才能保证「只要它跑了，就一定有记录」。
 *
 * <p>这个类同时做三件事，方向不同但互补：
 * <ul>
 *   <li>对外：把每次调用变成一个 Micrometer {@link Observation} → 指标（Prometheus）与
 *       span（OTLP）都能拿到，同一份埋点喂两个下游；</li>
 *   <li>对内：把每次调用记进 {@link SpanRecorder}，按 traceId 拼成调用树——
 *       这是「一次失败请求死在哪一层」的直接答案，也是无采集后端时的兜底；</li>
 *   <li>对父子关系：用线程上的「当前片段」把嵌套调用连起来。没有它，一次请求里的模型、
 *       工具、RAG 会变成三个互不相连的根节点——那样的数据只能证明「都跑过」，不能证明「谁在谁里面」。</li>
 * </ul>
 *
 * <p>边界：**它不重试、不降级、不改变任何业务行为**，失败原样抛出。
 * 观测层一旦开始替业务做决定，排查时你就再也分不清「是业务失败还是观测把它弄失败了」。
 */
@Component
public class ObservedOperation {


    private final ObservationRegistry registry;
    private final SpanRecorder recorder;

    public ObservedOperation(ObservationRegistry registry, SpanRecorder recorder) {
        this.registry = registry;
        this.recorder = recorder;
    }

    /**
     * 包一段调用。
     *
     * @param name       观测名，建议用语义化命名（{@code genai.chat}、{@code genai.tool}）
     * @param layer      所属层：http / model / tool / rag / graph / a2a
     * @param attributes 附加的低基数事实（工具名、模型名等），不要塞正文
     */
    public <T> T observe(String name, String layer, Map<String, String> attributes, Supplier<T> action) {
        return observeInternal(name, layer, attributes, null, action);
    }

    public void observe(String name, String layer, Map<String, String> attributes, Runnable action) {
        observeInternal(name, layer, attributes, null, () -> {
            action.run();
            return null;
        });
    }

    /** 业务上「按契约拒绝」也算结果：它不是异常，但必须能被统计（例如 RAG 无依据拒答）。 */
    public <T> T observeWithOutcome(String name, String layer, Map<String, String> attributes,
                                    Function<T, FailureType> outcomeOf, Supplier<T> action) {
        return observeInternal(name, layer, attributes, outcomeOf, action);
    }

    private <T> T observeInternal(String name, String layer, Map<String, String> attributes,
                                  Function<T, FailureType> outcomeOf, Supplier<T> action) {
        RequestContext context = RequestContext.orUnknown();
        Map<String, String> facts = new HashMap<>();
        facts.put("layer", layer);
        if (attributes != null) {
            facts.putAll(attributes);
        }

        Instant startedAt = Instant.now();
        String parentId = SpanScope.current();
        SpanRecorder.SpanHandle handle = recorder.startSpan(context.traceId(), parentId, layer, name,
                startedAt, facts);

        // 失败分类的标签**必须在建指标时就存在**，哪怕值是 none：
        // Prometheus 要求同名指标的所有 meter 拥有一致的标签键集合，
        // 只在失败时补 failure.type 会让「带这个标签的那批」被整批丢弃——
        // 实测现象就是一行 WARN（registration has failed）加上指标里查不到任何失败分类，
        // 而业务日志一切正常，非常容易漏掉。
        Observation observation = Observation.createNotStarted(name, registry)
                .lowCardinalityKeyValue("layer", layer)
                .lowCardinalityKeyValue("failure.type", "none")
                .highCardinalityKeyValue("trace.id", context.traceId())
                .highCardinalityKeyValue("project.id", context.projectId())
                .highCardinalityKeyValue("session.id", context.sessionId())
                .highCardinalityKeyValue("thread.id", context.threadId());
        facts.forEach((key, value) -> {
            if (!"layer".equals(key)) {
                observation.lowCardinalityKeyValue(key, value);
            }
        });

        observation.start();
        String outcome = "ok";
        FailureType failureType = null;
        SpanScope.set(handle.spanId());
        try (Observation.Scope scope = observation.openScope()) {
            T result = action.get();
            if (outcomeOf != null) {
                failureType = outcomeOf.apply(result);
                if (failureType != null) {
                    outcome = "rejected";
                    observation.lowCardinalityKeyValue("failure.type", failureType.name());
                }
            }
            return result;
        } catch (RuntimeException ex) {
            failureType = FailureClassifier.classifyForLayer(layer, ex);
            outcome = "failed";
            observation.lowCardinalityKeyValue("failure.type", failureType.name());
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
            // 恢复上一层：片段栈必须原样还原，否则兄弟片段会互相认错父亲
            SpanScope.set(parentId);
            recorder.finish(handle, outcome, failureType,
                    Duration.between(startedAt, Instant.now()).toMillis());
        }
    }
}
