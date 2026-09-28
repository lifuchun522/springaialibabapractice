package com.example.digitalhuman.observability;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 身份的诞生地（第 17 掌）：请求进来第一件事就是定下四元组。
 *
 * <p><b>类名为什么不叫 {@code RequestContextFilter}</b>：Spring Boot 自己就注册了一个同名 Bean
 * （{@code org.springframework.web.filter.RequestContextFilter}），沿用同名会直接撞成
 * {@code BeanDefinitionOverrideException}——启动失败。这里踩过一次，改名的同时也把结论留下：
 * 与框架自带的类重名，不是「风格问题」，是启动期就会炸的硬冲突。
 *
 * <p>三条设计约束：
 * <ol>
 *   <li><b>继承优先于生成</b>：请求头里带了 {@code X-Trace-Id} 就沿用。这样跨进程调用
 *       （主服务 → 知识 Agent）才是同一条链，而不是两条各自漂亮的链；</li>
 *   <li><b>四元组在这一层定，下游只读不写</b>。让每层自己生成 traceId，等于每层造一个
 *       新的孤岛——这正是文章 01 章那三行日志互不相认的成因；</li>
 *   <li><b>出口回写</b>：响应头带 {@code X-Trace-Id}，用户截图投诉时我们能直接拿号去查，
 *       而不是让他「再复现一下」。</li>
 * </ol>
 *
 * <p>过滤器只做绑定与清理，不做业务判断：可观测层不改变业务行为，也不负责重试与降级——
 * 把重试塞进观测层是这块做砸的第一个坑。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceContextFilter.class);

    private final ObservedOperation observed;
    private final io.micrometer.tracing.Tracer tracer;

    public TraceContextFilter(ObservedOperation observed, io.micrometer.tracing.Tracer tracer) {
        this.observed = observed;
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        String projectId = firstNonBlank(request.getHeader(RequestContext.HEADER_PROJECT_ID),
                request.getParameter("projectId"), "unknown");
        String sessionId = firstNonBlank(request.getHeader(RequestContext.HEADER_SESSION_ID),
                request.getParameter("sessionId"), "unknown");
        // threadId 与 Memory / 图检查点用的是同一个会话键（第 5、11 掌），
        // 对不上号就会出现「trace 找到了，历史读不回来」
        String threadId = projectId + ":" + sessionId;

        Instant startedAt = Instant.now();
        RequestContext context = new RequestContext(traceId, projectId, sessionId, threadId);
        RequestContext.bind(context);
        response.setHeader(RequestContext.HEADER_TRACE_ID, traceId);
        try {
            // 第 17 掌：整个请求也是一段调用。少了这一层，**失败得越早越看不到**——
            // 入参非法、项目不存在这类错在进入业务埋点之前就抛了，
            // 诊断面上会是「一棵空树」，而空树恰恰出现在最需要答案的时候（实测踩到过）。
            observed.observeWithOutcome("http.request", "http",
                    java.util.Map.of("method", request.getMethod(), "path", request.getRequestURI()),
                    // 按**响应状态码**判成败，而不是按异常：@ExceptionHandler 在 Servlet 内部
                    // 就把异常转成响应了，过滤器看不到异常——实测「树有了、失败分类是空的」就是这个原因。
                    ignored -> FailureClassifier.classifyHttpStatus(response.getStatus()),
                    () -> {
                        try {
                            chain.doFilter(request, response);
                            return null;
                        } catch (java.io.IOException | ServletException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        } finally {
            // 第 17 掌：一行访问日志，带上四元组与耗时。它的价值在「没有它就没有基线」——
            // 排查时第一个要回答的问题永远是「这次请求是什么时候、多慢、结果如何」，
            // 而这一行是唯一在**任何**请求上都会出现的记录（业务日志只在业务代码走到时才有）。
            log.info("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), Duration.between(startedAt, Instant.now()).toMillis());
            RequestContext.clear();
        }
    }

    /**
     * traceId 的解析顺序，**这一段的顺序是踩出来的**（第 17 掌真实验收）。
     *
     * <p>第一版直接自己生成 UUID，结果同一个请求出现了两个号：
     * 响应头与调用树是我们生成的 32 位号，而日志里的 {@code traceId} 是另一个 32 位号——
     * 因为 Micrometer Tracing 的 correlation 装饰器**也往 MDC 里写 {@code traceId}**，
     * 它写的是框架自己那条 span 的 traceId。同一个 key 被两套系统抢着写，
     * 日志和响应头就永远对不上（实测：header=a6d63cf3…、日志=6d5f033b…）。
     *
     * <p>修法就是文章 V1 里的写法：**优先用框架当前 span 的 traceId**，让身份只有一个来源。
     * 非 HTTP 入口或没有 span 时，才退回请求头（跨进程手传递的号）与自生成。
     * 跨进程的标准做法是 W3C {@code traceparent}，由框架自动接续；{@code X-Trace-Id}
     * 只是给「不带链路头的调用方」留的手动通道。
     */
    private String resolveTraceId(HttpServletRequest request) {
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        String incoming = request.getHeader(RequestContext.HEADER_TRACE_ID);
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && !"unknown".equals(candidate)) {
                return candidate;
            }
        }
        return "unknown";
    }
}
