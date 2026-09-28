package com.example.knowledgeagent.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 把知识 Agent 注册到 Nacos。
 *
 * <p>为什么注册中心是可选的（默认关着也能跑）：它不承载业务语义，
 * 只承载「谁能被找到」。这一点在工程上很重要——
 * 本地单实例开发时不需要它，而**多实例**（本掌验收第二条）才真正需要：
 * 没有注册中心，主服务只能靠配置文件写死实例，扩缩容就必须改配置。
 *
 * <p>{@code a2a.register.enabled=false} 时这个配置类完全不生效，服务照常启动。
 */
@Configuration
@ConditionalOnProperty(name = "a2a.register.enabled", havingValue = "true")
public class NacosRegistrar {

    private static final Logger log = LoggerFactory.getLogger(NacosRegistrar.class);

    private final String serverAddr;
    private final String serviceName;
    private final String instanceId;
    private final int port;

    private NamingService namingService;

    public NacosRegistrar(@Value("${a2a.register.server-addr}") String serverAddr,
                          @Value("${a2a.register.service-name}") String serviceName,
                          @Value("${a2a.instance-id}") String instanceId,
                          @Value("${server.port:8082}") int port) {
        this.serverAddr = serverAddr;
        this.serviceName = serviceName;
        this.instanceId = instanceId;
        this.port = port;
    }

    @PostConstruct
    void register() throws Exception {
        namingService = NacosFactory.createNamingService(serverAddr);
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(port);
        instance.setServiceName(serviceName);
        // 实例标识进 metadata：主服务选实例、对账日志都要用它
        instance.getMetadata().put("instance-id", instanceId);
        instance.getMetadata().put("a2a-version", com.example.knowledgeagent.A2AProtocol.VERSION);
        namingService.registerInstance(serviceName, instance);
        log.info("[nacos] 已注册：{}@{}:{} metadata={}", serviceName, instance.getIp(), port,
                instance.getMetadata());
    }

    @PreDestroy
    void deregister() throws Exception {
        if (namingService != null) {
            namingService.shutDown();
            log.info("[nacos] 已注销：{}", instanceId);
        }
    }
}
