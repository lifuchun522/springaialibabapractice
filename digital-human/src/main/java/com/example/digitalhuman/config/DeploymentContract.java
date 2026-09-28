package com.example.digitalhuman.config;

import java.util.List;

import org.springframework.core.env.Environment;

/**
 * 部署契约：**哪些配置项是必需的、缺了会怎样**。
 *
 * <p>第 16 掌 03 讲的原理二是：一个服务能不能交付，取决于它在依赖缺失时的表现，
 * 而不是依赖正常时的表现。这条契约就是把「缺失时的表现」写下来的地方——
 * 它只回答两个问题：
 * <ol>
 *   <li>这一项是必填，还是有安全默认值？</li>
 *   <li>必填项缺失时，错误信息要说清「缺哪个环境变量、去哪儿声明」。</li>
 * </ol>
 *
 * <p><b>为什么要有这个类，而不是把校验散在两处</b>：启动期校验和健康检查如果各写一遍，
 * 必然漂移——启动期认为必需、健康检查认为可选（或反过来），这种不一致比不检查更坏：
 * 它让你以为有人在把关。所以这里是**唯一真源**：
 * {@link DeploymentContractInitializer}（启动期）与
 * {@code config.health.ConfigReadinessHealthIndicator}（运行期）都读它。
 *
 * <p>判定「必需」的标准是文章给的那条：<b>没有安全默认值</b>。
 * 端口、模型名有安全默认值（缺了也能正确运行），所以不是必需项；
 * 模型密钥缺了只能失败——给它一个占位默认值（{@code ${DEEPSEEK_API_KEY:sk-xxxx}}）
 * 会同时犯两个错：把凭据写回版本库，并把「启动期配置缺失」退化成更晚、更难定位的运行时错误。
 */
public final class DeploymentContract {

    /** 一个配置项在部署契约里的描述。 */
    public record RequiredItem(String key, String envVar, String hint) {

        /** 人类可读的缺失提示：点名环境变量、说明为什么不能给默认值、告诉人去哪儿声明。 */
        public String missingMessage() {
            return "部署契约不满足：缺少必需配置 " + key + "（环境变量 " + envVar + "）。"
                    + hint
                    + " 这一项没有安全默认值——给占位值只会把启动期错误推迟成第一次请求的失败。"
                    + "请在部署清单里声明它（deploy/.env、K8s Secret 或 docker run -e）。";
        }
    }

    /** 必需项：缺失即启动失败。 */
    public static final List<RequiredItem> REQUIRED = List.of(
            new RequiredItem("spring.ai.openai.api-key", "DEEPSEEK_API_KEY",
                    "它是模型通道的凭据，只允许从环境注入，禁止进镜像与版本库。"),
            new RequiredItem("digital-human.chat.default-system", "DIGITAL_HUMAN_DEFAULT_SYSTEM",
                    "它是数字人的人设，决定「模型扮演谁」；空人设会让所有回答失去角色边界。"));

    /** 可选项：缺失时服务仍能正确运行，只是某项能力关闭。 */
    public static final List<RequiredItem> OPTIONAL = List.of(
            new RequiredItem("digital-human.a2a.instances", "A2A_INSTANCES",
                    "为空表示没有跨服务实例可调，A2A 入口会明确报错而不是静默返回兜底答案。"),
            new RequiredItem("digital-human.a2a.nacos.server-addr", "A2A_NACOS_SERVER_ADDR",
                    "为空且 nacos.enabled=false 时使用静态实例表。"),
            new RequiredItem("spring.ai.mcp.client.streamable-http.connections.showroom.url",
                    "MCP_SERVER_URL", "为空时远程工具清单为空（按 fail-fast 配置决定是否启动失败）。"));

    private DeploymentContract() {
    }

    /** 返回缺失的必需项；全部齐备时返回空列表（健康检查与启动校验共用这一条判定）。 */
    public static List<RequiredItem> missingRequired(Environment environment) {
        return REQUIRED.stream()
                .filter(item -> {
                    String value = environment.getProperty(item.key());
                    return value == null || value.isBlank();
                })
                .toList();
    }

    /**
     * 启动期校验：缺失就抛，错误信息直接指出缺哪个环境变量。
     *
     * <p>放在「解析完配置、创建任何 Bean 之前」执行——这是整个进程里最便宜的时刻：
     * 此时没有连接池、没有远端调用、没有半初始化的状态需要回滚。
     */
    public static void validate(Environment environment) {
        List<RequiredItem> missing = missingRequired(environment);
        if (!missing.isEmpty()) {
            StringBuilder message = new StringBuilder();
            for (RequiredItem item : missing) {
                message.append(item.missingMessage()).append('\n');
            }
            throw new IllegalStateException(message.toString().trim());
        }
    }
}
