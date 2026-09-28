package com.example.digitalhuman.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * 给一次调用固定住模型参数的装饰器。
 *
 * <p>为什么需要它（第 10 掌）：编排层里有一类调用**不该有采样自由度**——
 * 路由分类、抽取、完整性判断。它们的输出会被代码当条件用，一旦漂移，
 * 表现就是「同一句话有时候走售前、有时候走售后」。而模型客户端默认参数通常带着温度。
 *
 * <p>做法很直白：包一层，把每次 Prompt 的 options 换成固定值，别的什么都不改。
 * 不继承、不反射、不动业务代码——**把「确定性」限制在需要确定性的那几次调用上**，
 * 生成类节点（回答、追问措辞）仍然保留原有自由度。
 *
 * <p>与第 4 掌的 {@code ChatOptionsFactory} 的分工：那个按「模型目录 + 项目配置」算出参数，
 * 这个只负责「这一次调用必须用我给的参数」，是编排层的局部约束。
 *
 * <p>注意这里**不做合并**：调用方在 Prompt 上带的 options 会被整个替换掉。
 * 理由是这类调用的语义就是「按我说的参数来」；如果要保留调用方的参数，
 * 那就不该用这个装饰器，而不是让它「看情况生效」。
 */
public class FixedOptionsChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ChatOptions options;

    public FixedOptionsChatModel(ChatModel delegate, ChatOptions options) {
        this.delegate = delegate;
        this.options = options;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate.call(withOptions(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(withOptions(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return options;
    }

    /** 只换参数，不动消息：真正的业务输入必须原样传下去。 */
    private Prompt withOptions(Prompt prompt) {
        return new Prompt(prompt.getInstructions(), options);
    }
}
