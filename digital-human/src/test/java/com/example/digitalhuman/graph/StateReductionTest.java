package com.example.digitalhuman.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 归约策略的实验：**为什么「节点没错、结果错了」在这里是常态**。
 *
 * <p>这个测试不依赖 Spring、不依赖模型，用的是一张最小的图：
 * 两个并行节点往同一个 key 写值，唯一的变化是那个 key 的策略。
 *
 * <p>结论就是本掌要记住的一句话：**并行不是「加个线程池」，并行真正要处理的是合并语义。**
 * 策略配错不会抛异常、不会打日志，只会很安静地把先到的值挤掉——
 * 文章里那次「并行跑完之后知识片段整段消失」就是这个原因。
 */
class StateReductionTest {

    private static final String HITS = "hits";

    /** 两个并行节点：一个写「知识库命中的片段」，一个写「业务记录补充的片段」。 */
    private static Map<String, Object> parallelGraph(KeyStrategyFactory strategies) throws Exception {
        StateGraph graph = new StateGraph("reduction-demo", strategies);
        graph.addNode("knowledge", AsyncNodeAction.node_async(state -> Map.of(HITS, List.of("知识库片段"))));
        graph.addNode("biz", AsyncNodeAction.node_async(state -> Map.of(HITS, List.of("业务记录"))));
        graph.addNode("collect", AsyncNodeAction.node_async(state -> Map.of()));

        graph.addEdge(StateGraph.START, "knowledge");
        graph.addEdge(StateGraph.START, "biz");
        graph.addEdge(List.of("knowledge", "biz"), "collect");
        graph.addEdge("collect", StateGraph.END);

        CompiledGraph compiled = graph.compile();
        OverAllState state = compiled.invoke(Map.of("input", "退款"),
                        RunnableConfig.builder().threadId("reduction-1").build())
                .orElseThrow();
        return state.data();
    }

    private static KeyStrategyFactory strategiesWith(KeyStrategy hitsStrategy) {
        return () -> {
            Map<String, KeyStrategy> map = new LinkedHashMap<>();
            map.put("input", KeyStrategy.REPLACE);
            map.put(HITS, hitsStrategy);
            return map;
        };
    }

    @Test
    @DisplayName("appendStrategy_shouldKeepBothParallelBranchOutputs")
    void appendStrategy_shouldKeepBothParallelBranchOutputs() throws Exception {
        Map<String, Object> state = parallelGraph(strategiesWith(KeyStrategy.APPEND));

        // 累积策略：两个分支的产出都在
        assertThat(AfterSaleState.asTextList(state.get(HITS)))
                .containsExactlyInAnyOrder("知识库片段", "业务记录");
    }

    @Test
    @DisplayName("replaceStrategy_shouldSilentlyLoseOneBranchOutput")
    void replaceStrategy_shouldSilentlyLoseOneBranchOutput() throws Exception {
        Map<String, Object> state = parallelGraph(strategiesWith(KeyStrategy.REPLACE));

        // 覆盖策略：只剩一个分支的产出——**没有异常、没有日志**，这就是最危险的地方。
        // 这也是为什么本仓库的做法是「用到的每个 key 都显式声明策略」，一个都不留给默认行为。
        assertThat(AfterSaleState.asTextList(state.get(HITS))).hasSize(1);
    }

    @Test
    @DisplayName("undeclaredKey_shouldFallBackToReplaceJustLikeTheArticleSays")
    void undeclaredKey_shouldFallBackToReplaceJustLikeTheArticleSays() throws Exception {
        // 完全不声明这个 key：默认行为等价于 REPLACE，同样会丢
        KeyStrategyFactory onlyInput = () -> Map.of("input", KeyStrategy.REPLACE);

        Map<String, Object> state = parallelGraph(onlyInput);

        assertThat(AfterSaleState.asTextList(state.get(HITS))).hasSize(1);
    }
}
