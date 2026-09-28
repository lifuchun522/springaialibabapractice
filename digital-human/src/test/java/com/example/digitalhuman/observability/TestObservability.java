package com.example.digitalhuman.observability;

import io.micrometer.observation.ObservationRegistry;

/**
 * 测试用的观测入口工厂。
 *
 * <p>单测里手写 {@code new ObservedOperation(ObservationRegistry.NOOP, new SpanRecorder())} 太长，
 * 而且很容易有人顺手传 {@code null}——传 null 会让「这条链路本来就没埋点」和
 * 「测试忘了接观测」看起来一模一样，正是本掌要消灭的那类模糊。
 */
public final class TestObservability {

    private TestObservability() {
    }

    /** 不落指标、只记调用树的观测入口：单测断言调用逻辑时用它，断言埋点时用带 registry 的那个。 */
    public static ObservedOperation noop() {
        return new ObservedOperation(ObservationRegistry.NOOP, new SpanRecorder(50));
    }

    public static ObservedOperation withRegistry(ObservationRegistry registry) {
        return new ObservedOperation(registry, new SpanRecorder(50));
    }
}
