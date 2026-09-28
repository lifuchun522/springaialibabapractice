package com.example.digitalhuman.config.health;

import java.util.List;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.digitalhuman.config.DeploymentContract;

/**
 * 配置就绪健康检查：探的是「关键配置齐没齐」，**不做任何远程调用**。
 *
 * <p>第 16 掌 03 讲原理二：健康检查不是监控，它是分层的一部分——
 * 它决定「配置校验放在哪一层、什么时候生效」。放在这里（Infrastructure 侧），
 * 缺失就在启动期与探针上暴露；随手放在 Controller 里，缺失就只能在第一次请求时暴露。
 *
 * <p>为什么不顺便探一下模型能不能通：被动探针会被调用方按秒级频率打，
 * 每一次都真调模型就是真花钱；而且模型侧抖动会把「我的服务没配好」和
 * 「对方今天不舒服」混成一个红点，运维照着这个红点排查只会更慢。
 * 这条边界与文章一致：探配置，不探依赖。
 *
 * <p>它读的是 {@link DeploymentContract}——与启动期校验同一份清单：
 * 两处各写一遍必然漂移，而「启动期认为必需、健康检查认为可选」这种不一致
 * 比不检查更坏，因为它让你以为有人在把关。
 */
@Component("configReadiness")
public class ConfigReadinessHealthIndicator implements HealthIndicator {

    private final Environment environment;

    public ConfigReadinessHealthIndicator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Health health() {
        List<DeploymentContract.RequiredItem> missing = DeploymentContract.missingRequired(environment);

        Health.Builder builder = missing.isEmpty() ? Health.up() : Health.down();
        for (DeploymentContract.RequiredItem item : DeploymentContract.REQUIRED) {
            boolean present = missing.stream().noneMatch(miss -> miss.key().equals(item.key()));
            builder.withDetail(item.key(), present ? "present" : "MISSING(env " + item.envVar() + ")");
        }
        for (DeploymentContract.RequiredItem item : DeploymentContract.OPTIONAL) {
            String value = environment.getProperty(item.key());
            builder.withDetail(item.key(), value == null || value.isBlank() ? "unset(optional)" : "set");
        }
        if (!missing.isEmpty()) {
            builder.withDetail("reason", "缺失必需配置 " + missing.stream()
                    .map(DeploymentContract.RequiredItem::envVar).toList());
        }
        return builder.build();
    }
}
