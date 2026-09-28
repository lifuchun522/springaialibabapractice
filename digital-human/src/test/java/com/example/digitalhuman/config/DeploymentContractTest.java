package com.example.digitalhuman.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

import com.example.digitalhuman.config.health.ConfigReadinessHealthIndicator;

/**
 * 第 16 掌：部署契约本身要被断言。
 *
 * <p>这一掌交付的四条完成标准里，有两条落在「配置缺失时的行为」上：
 * 启动期必须失败、失败信息必须指出缺哪个环境变量。这两条如果只写进文档，
 * 就会在下一次「先让它跑起来」的临时改动里悄悄失效——所以它们在这里被钉住。
 */
class DeploymentContractTest {

    private static MockEnvironment completeEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "sk-test-not-a-real-key")
                .withProperty("digital-human.chat.default-system", "你是一个专业的数字人主播。")
                .withProperty("digital-human.a2a.instances", "http://127.0.0.1:8082")
                .withProperty("digital-human.a2a.nacos.server-addr", "127.0.0.1:8848")
                .withProperty("spring.ai.mcp.client.streamable-http.connections.showroom.url",
                        "http://localhost:8081");
    }

    @Test
    @DisplayName("配置齐备时启动校验通过，缺失项列表为空")
    void shouldPassWhenEverythingPresent() {
        MockEnvironment environment = completeEnvironment();

        assertThat(DeploymentContract.missingRequired(environment)).isEmpty();
        assertThatCode(() -> DeploymentContract.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺模型密钥即启动失败，且错误信息点名环境变量与去处")
    void shouldFailFastWithoutModelKey() {
        MockEnvironment environment = completeEnvironment().withProperty("spring.ai.openai.api-key", "");

        assertThatThrownBy(() -> DeploymentContract.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.ai.openai.api-key")
                .hasMessageContaining("DEEPSEEK_API_KEY")
                .hasMessageContaining("没有安全默认值")
                .hasMessageContaining("deploy/.env");
    }

    @Test
    @DisplayName("人设同样属于必需项：空人设会让回答失去角色边界")
    void shouldFailFastWithoutSystemPrompt() {
        MockEnvironment environment = completeEnvironment()
                .withProperty("digital-human.chat.default-system", "   ");

        assertThatThrownBy(() -> DeploymentContract.validate(environment))
                .hasMessageContaining("digital-human.chat.default-system")
                .hasMessageContaining("DIGITAL_HUMAN_DEFAULT_SYSTEM");
    }

    @Test
    @DisplayName("可选项缺失不影响启动：缺了只是关掉某项能力")
    void optionalItemsMayBeMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "sk-test-not-a-real-key")
                .withProperty("digital-human.chat.default-system", "人设");

        assertThatCode(() -> DeploymentContract.validate(environment)).doesNotThrowAnyException();
        assertThat(DeploymentContract.OPTIONAL).isNotEmpty();
    }

    @Test
    @DisplayName("健康检查与启动校验读同一份清单：缺密钥时 DOWN，且细节里写清缺哪一项")
    void readinessShouldShareTheSameContract() {
        Health down = new ConfigReadinessHealthIndicator(
                completeEnvironment().withProperty("spring.ai.openai.api-key", "")).health();

        assertThat(down.getStatus()).isEqualTo(Status.DOWN);
        assertThat(down.getDetails().get("spring.ai.openai.api-key")).isEqualTo("MISSING(env DEEPSEEK_API_KEY)");
        assertThat(down.getDetails().get("reason").toString()).contains("DEEPSEEK_API_KEY");

        Health up = new ConfigReadinessHealthIndicator(completeEnvironment()).health();

        assertThat(up.getStatus()).isEqualTo(Status.UP);
        assertThat(up.getDetails().get("spring.ai.openai.api-key")).isEqualTo("present");
        // 可选项要显示成「未设置（可选）」而不是伪装成问题
        assertThat(up.getDetails().get("digital-human.a2a.nacos.server-addr")).isEqualTo("set");
    }
}
