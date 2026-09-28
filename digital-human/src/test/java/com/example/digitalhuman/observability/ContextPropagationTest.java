package com.example.digitalhuman.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 第 17 掌：身份必须跨线程池活着，而且不能串号。
 *
 * <p>这条用例针对的是文章 06 章那个经典故障：MDC 是 ThreadLocal，
 * 一旦跨过线程池（工具执行池、SSE 异步执行器、图节点线程）就丢了，
 * 于是「同一次请求」的日志又变成互不相认的几段。
 *
 * <p>两个方向都要测，只测一个方向等于没测：
 * <ul>
 *   <li><b>传播</b>：任务在池线程里能读到提交时的身份；</li>
 *   <li><b>不串号</b>：池线程复用后，下一个任务读不到上一个请求的身份。</li>
 * </ul>
 */
class ContextPropagationTest {

    private static final RequestContext FIRST =
            new RequestContext("trace-aaaa", "1", "sessions-1", "1:sessions-1");
    private static final RequestContext SECOND =
            new RequestContext("trace-bbbb", "2", "sessions-2", "2:sessions-2");

    @Test
    @DisplayName("线程池任务能读到提交时的身份：traceId 不再只活在 Servlet 线程里")
    void executorShouldPropagateIdentity() throws Exception {
        ExecutorService pool = new ContextAwareExecutorService(Executors.newSingleThreadExecutor());
        try {
            AtomicReference<RequestContext> seenInPool = new AtomicReference<>();
            RequestContext.bind(FIRST);
            pool.submit(() -> seenInPool.set(RequestContext.current())).get(5, TimeUnit.SECONDS);

            assertThat(seenInPool.get()).isEqualTo(FIRST);
        } finally {
            RequestContext.clear();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("池线程复用不串号：上一个请求的身份必须被清干净")
    void reusedThreadShouldNotLeakPreviousIdentity() throws Exception {
        ExecutorService pool = new ContextAwareExecutorService(Executors.newSingleThreadExecutor());
        try {
            RequestContext.bind(FIRST);
            pool.submit(() -> { }).get(5, TimeUnit.SECONDS);
            RequestContext.clear();

            // 第二个任务在同一个池线程上执行，但它没有绑定身份：必须读到 null
            AtomicReference<RequestContext> leaked = new AtomicReference<>(FIRST);
            pool.submit(() -> leaked.set(RequestContext.current())).get(5, TimeUnit.SECONDS);

            assertThat(leaked.get()).as("线程池复用后残留的身份会把两次请求串在一起，比丢号更难查").isNull();
        } finally {
            RequestContext.clear();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("并发提交各自带自己的身份：十个任务不会互相污染")
    void concurrentTasksKeepTheirOwnIdentity() throws Exception {
        ExecutorService pool = new ContextAwareExecutorService(Executors.newFixedThreadPool(4));
        int tasks = 10;
        CountDownLatch ready = new CountDownLatch(tasks);
        List<String> observed = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            for (int index = 0; index < tasks; index++) {
                int current = index;
                RequestContext.bind(new RequestContext("trace-" + current, String.valueOf(current),
                        "s-" + current, current + ":s-" + current));
                pool.submit(() -> {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    observed.add(RequestContext.orUnknown().traceId());
                    ready.countDown();
                });
            }
            RequestContext.clear();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(observed).hasSize(tasks).doesNotHaveDuplicates();
            for (int index = 0; index < tasks; index++) {
                assertThat(observed).contains("trace-" + index);
            }
        } finally {
            RequestContext.clear();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("MDC 与 ThreadLocal 一起写、一起清：日志能看到身份，也不会污染下一个请求")
    void mdcFollowsTheContext() {
        RequestContext.bind(FIRST);
        assertThat(org.slf4j.MDC.get(RequestContext.MDC_TRACE_ID)).isEqualTo("trace-aaaa");
        assertThat(org.slf4j.MDC.get(RequestContext.MDC_THREAD_ID)).isEqualTo("1:sessions-1");

        RequestContext.bind(SECOND);
        assertThat(org.slf4j.MDC.get(RequestContext.MDC_TRACE_ID)).isEqualTo("trace-bbbb");

        RequestContext.clear();
        assertThat(org.slf4j.MDC.get(RequestContext.MDC_TRACE_ID)).isNull();
        assertThat(RequestContext.current()).isNull();
    }
}
