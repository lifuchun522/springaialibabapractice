package com.example.digitalhuman.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编排层的运行时边界与节点提示词（全部是配置，不是代码里的魔数）。
 *
 * <p>为什么连提示词也放这里：第 8 掌定下「提示词属于配置，改措辞不该改代码」。
 * 编排层尤其如此——节点提示词的措辞直接决定节点契约是否被遵守。
 *
 * @param routeInstruction  路由节点的判断口径。**必须写明「只能返回一个」**：
 *                          框架的 LlmRoutingAgent 默认允许一次返回多个子 Agent 并行执行，
 *                          那会让「这次请求走了几条分支」变成不确定的（见 docs/ch10-验收记录.md）
 * @param routeTemperature  路由分类调用的温度。分类是「要被代码当条件用」的输出，
 *                          **不该有采样自由度**：实测同一批 20 组样本、同一份口径连续跑三次，
 *                          命中 17 / 16 / 16，其中一条「预约余位」的问题在三次里翻了面。
 *                          压到 0 之后重测见 docs/ch10-验收记录.md
 * @param loopMaxRounds     补信息循环的最大轮数：缺信息要追问，但不能无限追问
 * @param understandInstruction 理解节点：只抽取，不回答
 * @param retrieveInstruction   检索节点：只取原文片段与来源，不总结
 * @param answerInstruction     回答节点：只能基于上游片段作答，并标出来源
 * @param mergeInstruction      归并节点：把两条并行分支的结果合成一份材料
 * @param clarifyInstruction    追问节点：信息不足时生成一句追问（不猜）
 */
@ConfigurationProperties(prefix = "digital-human.workflow")
public record WorkflowProperties(String routeInstruction,
                                 double routeTemperature,
                                 int loopMaxRounds,
                                 String understandInstruction,
                                 String retrieveInstruction,
                                 String answerInstruction,
                                 String mergeInstruction,
                                 String clarifyInstruction) {

    public WorkflowProperties {
        if (routeTemperature < 0) {
            routeTemperature = 0.0;
        }
        if (routeInstruction == null || routeInstruction.isBlank()) {
            // 这份口径是**实测改出来的**：第一版只写「产品能力/价格/试用/选型/私有化部署」，
            // 真实模型把「你们展厅几点关门」「深圳展厅还有停车位吗」判成了售前咨询（因为口径没说清参观信息算哪边），
            // 二十组样本命中 17 组。把参观类信息（开放时间、地址、停车、联系方式）显式归到售前之后重新测，
            // 见 docs/ch10-验收记录.md。口径含糊时，路由不准不全是模型的错。
            routeInstruction = """
                    你负责把用户的问题分给一个处理分支，只能返回一个分支：
                    - presale：售前与参观咨询（产品能力、价格、试用、选型、私有化部署、
                      参观预约怎么约、展厅开放时间、地址与停车、联系方式）
                    - aftersale：售后与业务查询（已购产品的使用、故障、进度、预约余位、
                      改约与取消、工单、设备送修）
                    - fallback：与上述两类都无关的其它问题（例如写代码、写文章、通用闲聊）
                    只依据用户当前这一句话判断，不要推理多轮，不要返回多个分支。
                    """;
        }
        if (loopMaxRounds <= 0) {
            loopMaxRounds = 2;
        }
        if (understandInstruction == null || understandInstruction.isBlank()) {
            understandInstruction = """
                    你是理解节点，只做三件事：
                    1) 用一句话复述用户诉求；
                    2) 抽取实体：产品名、问题类型、时间范围、订单号/预约号（没有的写「无」）；
                    3) 列出当前缺失、但回答所必需的信息。
                    禁止直接回答用户，只输出这三段结构化结果。
                    """;
        }
        if (retrieveInstruction == null || retrieveInstruction.isBlank()) {
            retrieveInstruction = """
                    你是检索节点。基于上游抽取出的实体调用知识库检索工具，
                    只返回检索到的原文片段与来源标识（docName#chunkIndex）。
                    不要总结，不要作答，不要补充片段里没有的内容。
                    """;
        }
        if (answerInstruction == null || answerInstruction.isBlank()) {
            answerInstruction = """
                    你是回答节点。只能基于上游给出的检索片段与业务记录组织回答，
                    片段里没有的内容不要补充，也不要用你自己的常识补齐。
                    回答末尾用 [n] 标出引用的是哪一条来源。回答简短、口语化。
                    """;
        }
        if (mergeInstruction == null || mergeInstruction.isBlank()) {
            mergeInstruction = """
                    你是归并节点。上游有两条并行分支的结果：
                    - knowledge_hits：知识库片段
                    - biz_records：业务系统记录（如展厅预约余位）
                    请把两者合成一份材料，分别标注「知识库」与「业务系统」两段，
                    不要丢掉任何一边，也不要替用户下结论。
                    """;
        }
        if (clarifyInstruction == null || clarifyInstruction.isBlank()) {
            clarifyInstruction = """
                    你是追问节点。上游判断出信息不足，你要生成一句面向用户的追问，
                    只问最关键的那一个缺失项，不要罗列一堆问题，也不要顺手回答。
                    如果信息已经足够，直接输出「信息已足够」。
                    """;
        }
    }
}
