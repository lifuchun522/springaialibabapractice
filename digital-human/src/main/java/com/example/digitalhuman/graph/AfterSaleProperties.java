package com.example.digitalhuman.graph;

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 售后图编排的边界与提示词。
 *
 * @param riskAmountThreshold 金额阈值：业务记录里出现金额且超过它，就走人工确认。
 *                            **这是业务规则，写在配置里，不写在提示词里**
 * @param humanReviewIntents 需要人工确认的意图集合（退款、送修这类不可逆或涉钱的动作）
 * @param intentInstruction   意图分类节点的提示词（分类结果会被条件边当条件用 → 用固定温度调用）
 * @param replyInstruction    答复节点的提示词（生成类，保留采样自由度）
 * @param intentLabels        允许的意图标签；分类结果不在这几个里就落到 {@code OTHER}
 */
@ConfigurationProperties(prefix = "digital-human.after-sale")
public record AfterSaleProperties(double riskAmountThreshold,
                                  Set<String> humanReviewIntents,
                                  String intentInstruction,
                                  String replyInstruction,
                                  Map<String, String> intentLabels) {

    public AfterSaleProperties {
        if (riskAmountThreshold <= 0) {
            riskAmountThreshold = 500.0;
        }
        if (humanReviewIntents == null || humanReviewIntents.isEmpty()) {
            humanReviewIntents = Set.of("REFUND", "REPAIR");
        }
        if (intentInstruction == null || intentInstruction.isBlank()) {
            intentInstruction = """
                    你是售后意图分类节点。只输出一个标签，不要解释、不要标点：
                    REFUND（退款、退货、退钱）
                    REPAIR（送修、维修、返修、故障）
                    RESCHEDULE（改约、取消预约、改时间）
                    VISIT（参观、开放时间、地址、停车、怎么约）
                    OTHER（其它）
                    """;
        }
        if (replyInstruction == null || replyInstruction.isBlank()) {
            replyInstruction = """
                    你是售后答复节点。基于状态里已有的意图、知识库片段、业务记录与风险等级组织答复。
                    没有的事实不要编；需要人工处理的，就明确说已转人工。
                    回答简短、口语化，末尾用 [n] 标出引用的是哪一条来源。
                    """;
        }
        if (intentLabels == null || intentLabels.isEmpty()) {
            intentLabels = Map.of();
        }
    }
}
