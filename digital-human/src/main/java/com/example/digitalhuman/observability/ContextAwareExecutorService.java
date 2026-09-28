package com.example.digitalhuman.observability;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 把任意 {@link ExecutorService} 包成「会传播身份」的版本。
 *
 * <p>为什么要包一层而不是改调用方：工具执行、Graph 异步节点、SSE 异步执行器各有各的池，
 * 让每个调用点自己记得「先捕获再恢复」，结果一定是有人忘。**身份传播是执行器的性质，不是调用者的纪律**——
 * 这一条与第 16 掌的 profile 隔离同理：边界靠结构维持，不靠自觉。
 *
 * <p>注意它只装饰任务的提交，不改池的大小、队列与拒绝策略：观测层不改变并发语义。
 */
public class ContextAwareExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;

    public ContextAwareExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(ContextAwareTaskDecorator.decorateRunnable(command));
    }

    @Override
    public java.util.concurrent.Future<?> submit(Runnable task) {
        return delegate.submit(ContextAwareTaskDecorator.decorateRunnable(task));
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
        return delegate.submit(ContextAwareTaskDecorator.decorateRunnable(task), result);
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
        return delegate.submit(ContextAwareTaskDecorator.decorateCallable(task));
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
