package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.digitalhuman.observability.RequestContext;
import com.example.digitalhuman.observability.SpanRecorder;
import com.example.digitalhuman.service.AuthService;

/**
 * 诊断面（第 17 掌）：拿一个 traceId，把这次请求的调用树取回来。
 *
 * <p>它回答的是本掌要解决的那个问题——**一条失败请求到底死在模型、工具、RAG、图节点还是远程 Agent 上**。
 * 在这之前，答案是 grep 日志加肉眼对时间戳；现在是一棵带层名、耗时、结果与失败分类的树。
 *
 * <p>三条边界要说清：
 * <ul>
 *   <li><b>要登录</b>：诊断面暴露的是内部结构与耗时分布，不能匿名可读（与第 16 掌
 *       「访问它的人是不是开发者/内网组件」同一判据）；</li>
 *   <li><b>只回结构与结果，不回正文</b>：录进去的本来就不含用户输入与模型输出
 *       （见 {@code SpanRecorder}），所以这里也不可能漏出去；</li>
 *   <li><b>它是诊断缓冲，不是存储</b>：只保留最近若干次请求，长期留存走 OTLP 后端与指标聚合。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private final SpanRecorder recorder;
    private final AuthService authService;

    public DiagnosticsController(SpanRecorder recorder, AuthService authService) {
        this.recorder = recorder;
        this.authService = authService;
    }

    /** 单次请求的调用树。 */
    @GetMapping("/traces/{traceId}")
    public Map<String, Object> trace(@RequestHeader("X-Token") String token,
                                     @PathVariable String traceId) {
        authService.requireUserId(token);
        List<SpanRecorder.TraceNode> tree = recorder.treeOf(traceId);
        List<SpanRecorder.Span> spans = recorder.spansOf(traceId);
        return Map.of(
                "traceId", traceId,
                "spanCount", spans.size(),
                "failedSpans", spans.stream().filter(span -> !"ok".equals(span.outcome())).count(),
                "failureTypes", spans.stream()
                        .map(SpanRecorder.Span::failureType)
                        .filter(type -> type != null && !type.isBlank())
                        .distinct()
                        .toList(),
                "tree", tree);
    }

    /** 最近出现过的 traceId：出事时无需翻日志找号。 */
    @GetMapping("/traces")
    public Map<String, Object> recentTraces(@RequestHeader("X-Token") String token) {
        authService.requireUserId(token);
        return Map.of(
                "trackedTraceCount", recorder.trackedTraceCount(),
                "recentTraceIds", recorder.recentTraceIds(20),
                "currentTraceId", RequestContext.orUnknown().traceId());
    }
}
