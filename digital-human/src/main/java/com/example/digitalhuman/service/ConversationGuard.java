package com.example.digitalhuman.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 同一会话的串行闸门。
 *
 * <p>同一个 conversationId 的 Memory 窗口是一份有序状态：两条请求并行进来，
 * 谁先写窗口、谁后写窗口是不确定的，上下文顺序一乱，历史与回答就对不上。
 *
 * <p>做法刻意选得笨但确定：**不排队，直接拒绝**（409）。排队会引入无上限等待与内存堆积，
 * 而流式请求本来就有客户端这一层，重试比排队更容易解释。
 */
@Component
public class ConversationGuard {

    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(String conversationId) {
        return busy.add(conversationId);
    }

    public void release(String conversationId) {
        busy.remove(conversationId);
    }

    /** 仅供测试与排查：当前有多少会话在途。 */
    public int inFlight() {
        return busy.size();
    }
}
