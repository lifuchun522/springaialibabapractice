package com.example.digitalhuman.observability;

import java.util.concurrent.TimeoutException;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.ai.ModelRoutingException;
import com.example.digitalhuman.rag.KnowledgeContractException;
import com.example.digitalhuman.service.ConversationBusyException;
import com.example.digitalhuman.service.ResourceNotFoundException;

/**
 * 异常 → {@link FailureType} 的映射（第 17 掌）。
 *
 * <p>它复用系统里**已经有语义的异常**，而不是新造一套：
 * {@code ModelInvocationException.Kind} 从第 4 掌起就区分了超时/鉴权/空响应，
 * 这里只是把它翻译成指标与诊断面能聚合的分类，不重复定义一遍「什么算超时」。
 *
 * <p>映射表本身要有出处，所以它是被测过的（{@code FailureClassifierTest}）：
 * 分类一旦错，聚合出来的结论就会把人引向错误的层——比不分类更坏。
 */
public final class FailureClassifier {

    private FailureClassifier() {
    }

    /** 按异常类型分类；分不出就 {@link FailureType#UNKNOWN}（它变多说明分类表该补了）。 */
    public static FailureType classify(Throwable throwable) {
        Throwable ex = unwrap(throwable);
        if (ex == null) {
            return FailureType.UNKNOWN;
        }

        if (ex instanceof ModelInvocationException invocation) {
            return switch (invocation.kind()) {
                case TIMEOUT, UNAVAILABLE -> FailureType.MODEL_TIMEOUT;
                case AUTH, PROVIDER_ERROR, EMPTY_RESPONSE -> FailureType.MODEL_ERROR;
            };
        }
        if (ex instanceof ConversationBusyException) {
            return FailureType.CONVERSATION_BUSY;
        }
        if (ex instanceof KnowledgeContractException) {
            return FailureType.RAG_ERROR;
        }
        if (ex instanceof ModelRoutingException
                || ex instanceof IllegalArgumentException
                || ex instanceof ResponseStatusException status && status.getStatusCode().is4xxClientError()) {
            return FailureType.INPUT_INVALID;
        }
        if (ex instanceof ResourceNotFoundException) {
            return FailureType.INPUT_INVALID;
        }
        if (ex instanceof TimeoutException || ex instanceof ResourceAccessException) {
            // 这两类出现在远程 Agent / 工具侧，具体归属由调用点用 layer 补充
            return FailureType.REMOTE_AGENT_ERROR;
        }
        return FailureType.UNKNOWN;
    }

    /** 按「哪一层」细化：同一类异常在不同层含义不同（工具里的超时是 TOOL_ERROR）。 */
    public static FailureType classifyForLayer(String layer, Throwable throwable) {
        FailureType base = classify(throwable);
        if ("tool".equals(layer) && (base == FailureType.REMOTE_AGENT_ERROR || base == FailureType.UNKNOWN)) {
            return FailureType.TOOL_ERROR;
        }
        if ("a2a".equals(layer) && base == FailureType.UNKNOWN) {
            return FailureType.REMOTE_AGENT_ERROR;
        }
        if ("graph".equals(layer) && base == FailureType.UNKNOWN) {
            return FailureType.GRAPH_NODE_ERROR;
        }
        if ("rag".equals(layer) && base == FailureType.UNKNOWN) {
            return FailureType.RAG_ERROR;
        }
        return base;
    }

    /**
     * 按 HTTP 状态码分类（第 17 掌真实验收后补的一条）。
     *
     * <p>为什么需要它：{@code @ExceptionHandler} 会在 DispatcherServlet 内部把异常转成响应，
     * 于是**过滤器看不到任何异常**——HTTP 那一段的片段会是「ok」，
     * 而恰恰是这类早失败（项目不存在、入参非法）最需要被看见。实测就撞上了：树有了，
     * 失败分类却是空的。HTTP 层的事实本来就是状态码，按状态码分类比按异常分类更准。
     *
     * <p>5xx 一律 UNKNOWN：具体是哪一层坏的，由更内层的片段给出（模型/工具/RAG 各有分类），
     * 在这里猜一个只会和真相打架。
     */
    public static FailureType classifyHttpStatus(int status) {
        if (status < 400) {
            return null;
        }
        return switch (status) {
            case 400, 401, 403, 404, 405, 406, 415, 422 -> FailureType.INPUT_INVALID;
            case 409, 429 -> FailureType.CONVERSATION_BUSY;
            default -> FailureType.UNKNOWN;
        };
    }

    /** 剥掉包装异常（CompletionException / RuntimeException 之类），拿到真正有语义的那一层。 */
    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            if (current instanceof java.util.concurrent.CompletionException
                    || current instanceof java.util.concurrent.ExecutionException
                    || current instanceof RuntimeException && current.getClass() == RuntimeException.class) {
                current = current.getCause();
                continue;
            }
            break;
        }
        return current;
    }
}
