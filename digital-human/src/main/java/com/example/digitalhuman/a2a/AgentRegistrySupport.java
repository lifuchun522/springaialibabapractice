package com.example.digitalhuman.a2a;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

/**
 * 两种发现实现合一：**Nacos 可用就用 Nacos，不可用就用静态表**。
 *
 * <p>这么做不是为了少写代码，而是为了让「注册中心是可选中层」这句话在代码里成立：
 * 主服务不知道也不关心实例是从哪来的，它只知道「拿到一个实例，按协议调它」。
 *
 * <p>选实例用轮询而不是随机：多实例时**轮询能观察到确定的分散**（验收第二条要的就是「可观察」），
 * 随机在实例少的时候看起来像没生效。
 */
@Component
public class AgentRegistrySupport implements AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistrySupport.class);

    private final AtomicInteger cursor = new AtomicInteger();
    private final List<AgentInstance> staticInstances;
    private final boolean nacosEnabled;
    private final String nacosServerAddr;
    private final String nacosServiceName;

    private NamingService namingService;

    public AgentRegistrySupport(
            @Value("${digital-human.a2a.instances:http://127.0.0.1:8082}") List<String> instances,
            @Value("${digital-human.a2a.nacos.enabled:false}") boolean nacosEnabled,
            @Value("${digital-human.a2a.nacos.server-addr:127.0.0.1:8848}") String nacosServerAddr,
            @Value("${digital-human.a2a.nacos.service-name:knowledge-agent}") String nacosServiceName) {
        List<AgentInstance> parsed = new ArrayList<>();
        for (int i = 0; i < instances.size(); i++) {
            String url = instances.get(i).trim();
            if (!url.isEmpty()) {
                parsed.add(new AgentInstance(url.substring(url.lastIndexOf(':') + 1), url));
            }
        }
        this.staticInstances = List.copyOf(parsed);
        this.nacosEnabled = nacosEnabled;
        this.nacosServerAddr = nacosServerAddr;
        this.nacosServiceName = nacosServiceName;
    }

    @Override
    public List<AgentInstance> instances() {
        if (nacosEnabled) {
            try {
                List<Instance> healthy = namingService().selectInstances(nacosServiceName, true);
                if (!healthy.isEmpty()) {
                    List<AgentInstance> found = new ArrayList<>();
                    for (Instance instance : healthy) {
                        Object id = instance.getMetadata().get("instance-id");
                        found.add(new AgentInstance(
                                id == null ? instance.getInstanceId() : String.valueOf(id),
                                "http://" + instance.getIp() + ":" + instance.getPort()));
                    }
                    return found;
                }
                log.warn("[a2a] Nacos 上没有健康实例，退回静态表：{}", staticInstances);
            } catch (Exception ex) {
                // 注册中心不可用不该让主服务不可用：退回静态表并明确告警（失败要显式，但别把功能一起带走）
                log.warn("[a2a] Nacos 查询失败（{}），退回静态表", ex.getMessage());
            }
        }
        return staticInstances;
    }

    @Override
    public AgentInstance next() {
        List<AgentInstance> available = instances();
        if (available.isEmpty()) {
            throw new IllegalStateException("没有可用的知识 Agent 实例（静态表为空且 Nacos 无健康实例）");
        }
        int index = Math.floorMod(cursor.getAndIncrement(), available.size());
        return available.get(index);
    }

    private synchronized NamingService namingService() throws Exception {
        if (namingService == null) {
            namingService = NacosFactory.createNamingService(nacosServerAddr);
            log.info("[a2a] 已连接 Nacos {}", nacosServerAddr);
        }
        return namingService;
    }
}
