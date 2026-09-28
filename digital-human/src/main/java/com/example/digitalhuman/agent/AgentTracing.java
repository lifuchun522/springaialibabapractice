package com.example.digitalhuman.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;

/**
 * 把 Agent 内部的每一步都写进事件流：模型调了几次、工具调了哪个。
 *
 * <p>两条拦截器（而不是散落的日志语句）的理由：模型与工具是框架内部的节点，
 * 业务代码拿不到它们的调用点；拦截器是框架提供的、唯一稳定的接入位置。
 */
public final class AgentTracing {

    private static final Logger log = LoggerFactory.getLogger(AgentTracing.class);

    private AgentTracing() {
    }

    /** 记录每次模型调用，并累加到当前 trace 的模型调用次数。 */
    public static class ModelTracing extends ModelInterceptor {

        @Override
        public String getName() {
            return "model-tracing";
        }

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            AgentTrace trace = AgentTrace.current();
            int index = trace == null ? 0 : trace.countModelCall();
            log.info("[agent] trace={} node=model#{}", trace == null ? "no-trace" : trace.traceId(), index);
            return handler.call(request);
        }
    }

    /** 记录每次工具调用：工具名与 trace 一起构成可回放的顺序。 */
    public static class ToolTracing extends ToolInterceptor {

        @Override
        public String getName() {
            return "tool-tracing";
        }

        @Override
        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
            AgentTrace trace = AgentTrace.current();
            String toolName = request.getToolName() == null ? "unknown" : request.getToolName();
            if (trace != null) {
                trace.toolCalled(toolName);
            }
            log.info("[agent] trace={} node=tool:{}", trace == null ? "no-trace" : trace.traceId(), toolName);
            ToolCallResponse response = handler.call(request);
            log.info("[agent] trace={} node=tool:{} result={}",
                    trace == null ? "no-trace" : trace.traceId(), toolName, abbreviate(response));
            return response;
        }

        private static String abbreviate(ToolCallResponse response) {
            String text = response == null ? "" : String.valueOf(response.getResult());
            return text.length() <= 80 ? text : text.substring(0, 80) + "…";
        }
    }

    /** 仅供测试与排查：把 ToolContext 里的 traceId 暴露成事件（工具审计表也需要它）。 */
    static String traceIdOf(ToolContext context) {
        Object value = context == null ? null : context.getContext().get("traceId");
        return value == null ? null : String.valueOf(value);
    }
}
