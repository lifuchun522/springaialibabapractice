package com.example.digitalhuman.ai;

/** provider 不在模型目录里，或目录配置本身不合法。 */
public class ModelRoutingException extends RuntimeException {

    public ModelRoutingException(String message) {
        super(message);
    }
}
