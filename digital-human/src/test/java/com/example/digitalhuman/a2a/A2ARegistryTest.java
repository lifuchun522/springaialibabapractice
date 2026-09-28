package com.example.digitalhuman.a2a;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发现层与协议契约里能在离线环境钉住的部分。
 *
 * <p>三条断言对应这一掌最容易糊掉的三件事：
 * <ul>
 *   <li>多实例要被**分散**调用（轮询，可观察），不是总打第一个；</li>
 *   <li>没有可用实例时要**明确失败**，不能静默返回兜底答案；</li>
 *   <li>两端协议版本必须一致——这份副本与知识 Agent 那份是两个独立文件，靠测试保证不漂移。</li>
 * </ul>
 */
class A2ARegistryTest {

    private static AgentRegistrySupport registry(String... urls) {
        return new AgentRegistrySupport(List.of(urls), false, "127.0.0.1:8848", "knowledge-agent");
    }

    @Test
    @DisplayName("next_shouldRoundRobinAcrossInstancesSoTrafficIsObservable")
    void next_shouldRoundRobinAcrossInstancesSoTrafficIsObservable() {
        AgentRegistry registry = registry("http://127.0.0.1:8082", "http://127.0.0.1:8083");

        // 轮询而不是随机：实例少的时候随机看起来像「没生效」，验收要的是「可观察的分散」
        assertThat(registry.next().baseUrl()).isEqualTo("http://127.0.0.1:8082");
        assertThat(registry.next().baseUrl()).isEqualTo("http://127.0.0.1:8083");
        assertThat(registry.next().baseUrl()).isEqualTo("http://127.0.0.1:8082");
        assertThat(registry.instances()).hasSize(2);
    }

    @Test
    @DisplayName("next_shouldFailExplicitlyWhenNoInstanceIsAvailable")
    void next_shouldFailExplicitlyWhenNoInstanceIsAvailable() {
        AgentRegistry empty = registry();

        // 静默返回一个兜底答案是最贵的失败模式：调用方以为成功了，业务上什么都没查到
        assertThatThrownBy(empty::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有可用的知识 Agent 实例");
    }

    @Test
    @DisplayName("protocolVersion_shouldMatchBetweenClientAndServerCopies")
    void protocolVersion_shouldMatchBetweenClientAndServerCopies() {
        // 两端的协议常量在两份独立文件里（拆服务必须各写一份），
        // 所以要有东西保证它们不漂移——这就是「契约须自证」在工程上的最小落法
        assertThat(A2AProtocol.VERSION).isEqualTo("1.0");
        assertThat(A2AProtocol.VERSION_HEADER).isEqualTo("A2A-Version");
        assertThat(A2AProtocol.TRACE_HEADER).isEqualTo("A2A-Trace-Id");
    }
}
