package com.example.digitalhuman.tools;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.repository.ToolCallAuditRepository;

/**
 * 工具注册中心：只读工具与写工具分成两套，各自包上审计与超时。
 *
 * <p>「只读进自由循环、写工具进门禁通道」这条边界如果只写在文档里，迟早会被一次
 * 「顺手把写工具也注册上」改掉；放在这里，默认就取不到写工具。
 */
@Component
public class ToolRegistry {

    private final ToolCallback[] readOnlyCallbacks;
    private final ToolCallback[] writeCallbacks;

    public ToolRegistry(ReadOnlyTools readOnlyTools,
                        WriteTools writeTools,
                        ToolCallAuditRepository audits,
                        ExecutorService toolExecutor,
                        @Value("${digital-human.tools.timeout-ms:3000}") long timeoutMs) {
        this.readOnlyCallbacks = wrap(ToolCallbacks.from(readOnlyTools), audits, toolExecutor, timeoutMs);
        this.writeCallbacks = wrap(ToolCallbacks.from(writeTools), audits, toolExecutor, timeoutMs);
    }

    public ToolCallback[] readOnly() {
        return readOnlyCallbacks;
    }

    /** 写工具只有被显式请求时才交出去（默认不注册给模型）。 */
    public ToolCallback[] write() {
        return writeCallbacks;
    }

    private static ToolCallback[] wrap(ToolCallback[] callbacks, ToolCallAuditRepository audits,
                                       ExecutorService executor, long timeoutMs) {
        return Arrays.stream(callbacks)
                .map(callback -> (ToolCallback) new AuditingToolCallback(callback, audits, executor, timeoutMs))
                .toArray(ToolCallback[]::new);
    }
}
