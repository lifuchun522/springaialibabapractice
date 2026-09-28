package com.example.digitalhuman.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.repository.ToolCallAuditRepository;

/**
 * 工具注册中心：本地只读、本地写、远程（MCP）三套分开管。
 *
 * <p>三套待遇不同，理由也各不同：
 * <ul>
 *   <li><b>本地只读</b>：进模型自由的对话循环；</li>
 *   <li><b>本地写</b>：默认不注册，且自身还有人类确认这道门禁；</li>
 *   <li><b>远程（MCP）</b>：能力属于别的服务，我们只负责「调不调、怎么解释结果」，
 *       同样包一层审计——远程调用更要留痕，因为出问题时两边都要对账。</li>
 * </ul>
 *
 * <p>MCP Client 未启用时（例如单测），远程那套就是空的，不影响启动。
 */
@Component
public class ToolRegistry {

    private final ToolCallback[] readOnlyCallbacks;
    private final ToolCallback[] writeCallbacks;
    private final ToolCallback[] remoteCallbacks;
    private final ToolCallAuditRepository audits;
    private final ExecutorService toolExecutor;
    private final long timeoutMs;
    private final int maxRetries;

    public ToolRegistry(ReadOnlyTools readOnlyTools,
                        WriteTools writeTools,
                        ToolCallAuditRepository audits,
                        ExecutorService toolExecutor,
                        @Value("${digital-human.tools.timeout-ms:3000}") long timeoutMs,
                        @Value("${digital-human.tools.max-retries:2}") int maxRetries,
                        ObjectProvider<ToolCallbackProvider> remoteToolProviders) {
        this.audits = audits;
        this.toolExecutor = toolExecutor;
        this.timeoutMs = timeoutMs;
        this.maxRetries = Math.max(0, maxRetries);
        this.readOnlyCallbacks = wrap(ToolCallbacks.from(readOnlyTools));
        this.writeCallbacks = wrap(ToolCallbacks.from(writeTools));
        this.remoteCallbacks = wrapRemote(remoteToolProviders);
    }

    /**
     * 给「运行期动态生成的工具」补上同样的审计、超时与重试。
     * 第 9 掌的 Agent 工具是按请求绑定身份的，不能走构造期注册，但边界必须一致。
     */
    public ToolCallback[] wrapForAudit(ToolCallback[] callbacks) {
        return wrap(callbacks);
    }

    public ToolCallback[] readOnly() {
        return readOnlyCallbacks;
    }

    /** 写工具只有被显式请求时才交出去（默认不注册给模型）。 */
    public ToolCallback[] write() {
        return writeCallbacks;
    }

    /** 通过 MCP 发现的远程工具；Server 没起来时为空数组。 */
    public ToolCallback[] remote() {
        return remoteCallbacks;
    }

    private ToolCallback[] wrapRemote(ObjectProvider<ToolCallbackProvider> providers) {
        List<ToolCallback> collected = new ArrayList<>();
        providers.orderedStream().forEach(provider -> collected.addAll(Arrays.asList(provider.getToolCallbacks())));
        return wrap(collected.toArray(ToolCallback[]::new));
    }

    private ToolCallback[] wrap(ToolCallback[] callbacks) {
        return Arrays.stream(callbacks)
                .map(callback -> (ToolCallback) new AuditingToolCallback(
                        callback, audits, toolExecutor, timeoutMs, maxRetries))
                .toArray(ToolCallback[]::new);
    }
}
