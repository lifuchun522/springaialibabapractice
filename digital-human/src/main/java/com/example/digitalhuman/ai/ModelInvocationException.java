package com.example.digitalhuman.ai;

/**
 * 模型调用失败的统一语义。
 *
 * <p>业务层不 catch 任何一家 SDK 的异常——那等于把两家的报错都写死在业务代码里。
 * 调用点把底层异常收敛成这里的 {@link Kind}，由 web 层翻译成确定的 HTTP 响应。
 */
public class ModelInvocationException extends RuntimeException {

    public enum Kind {
        /** 提供方拒绝了密钥（上游鉴权失败）。 */
        AUTH,
        /** 连接/读取超时。 */
        TIMEOUT,
        /** 上游暂时不可用（限流、5xx）。 */
        UNAVAILABLE,
        /** 上游返回了错误，原因不在密钥也不在超时。 */
        PROVIDER_ERROR,
        /** 上游通了但内容为空——最容易伪装成「成功」的一种失败。 */
        EMPTY_RESPONSE
    }

    private final Kind kind;
    private final String provider;
    private final String model;

    public ModelInvocationException(Kind kind, String provider, String model, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.provider = provider;
        this.model = model;
    }

    public Kind kind() {
        return kind;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }
}
