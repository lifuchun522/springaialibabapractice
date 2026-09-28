package com.example.digitalhuman.service;

/** 同一会话已有在途请求：上下文窗口是有序状态，不能并发写。 */
public class ConversationBusyException extends RuntimeException {

    public ConversationBusyException(String conversationId) {
        super("该会话已有进行中的请求，请稍后再试：" + conversationId);
    }
}
