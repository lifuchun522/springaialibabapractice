# -*- coding: utf-8 -*-
"""把第 18 掌的「上线判据」变成可执行的清单校验（不需要集群）。

为什么不直接用 `kubectl apply --dry-run=client`：它仍然要连上 API Server 做 discovery
（本环境实测：`failed to download openapi: the server could not find the requested resource`）。
没有集群时，它能给的只有 YAML 语法对不对，给不了「策略写对了没有」。

这个脚本校验的是**这一掌真正要保住的东西**，每条都对应一个真实故障：

  * liveness 只探 /liveness（仅 ping）：把模型 API 或数据库写进 liveness，
    等于把一个外部故障放大成全集群震荡（Pod 反复重启，剩余副本被压垮）；
  * readiness 探 /readiness：没连上依赖的实例不许接流量；
  * startupProbe 存在：JVM 冷启动 + Flyway 迁移慢于 liveness 的耐心时，
    正确解法是 startup 探针，而不是把 liveness 的 initialDelay 调大；
  * maxUnavailable=0：滚动升级期间不出现容量缺口；
  * preStop + terminationGracePeriodSeconds ≥ 60：填掉「端点摘除还在传播」的窗口；
  * resources.requests 存在：没有 requests，HPA 的利用率算不出来（永远 unknown）；
  * runAsNonRoot + readOnlyRootFilesystem + 显式 tmp/logs 挂载：
    只读根文件系统之后，写日志的目录必须挂出来，否则启动即失败；
  * 记忆存储是 jdbc：多副本共享记忆，Pod 无状态（这一条是「状态可外置」的开关）；
  * PDB 的 selector 与 Deployment 的 pod 标签一致：不一致时 PDB 形同虚设。

用法：
    python scripts/validate-k8s-manifests.py            # 校验 deploy/k8s/*.yaml
    python scripts/validate-k8s-manifests.py --dir other
退出码 0 表示全部通过；非 0 时逐条打印违反项。
"""
import argparse
import os
import sys

import yaml

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = os.path.join(REPO, "deploy", "k8s")


def load_documents(directory):
    docs = []
    for name in sorted(os.listdir(directory)):
        if not name.endswith((".yaml", ".yml")):
            continue
        path = os.path.join(directory, name)
        with open(path, encoding="utf-8") as handle:
            for doc in yaml.safe_load_all(handle):
                if doc:
                    docs.append((name, doc))
    return docs


def find(docs, kind):
    return [(name, doc) for name, doc in docs if doc.get("kind") == kind]


def container_of(deployment):
    return deployment["spec"]["template"]["spec"]["containers"][0]


def probe_path(container, probe_name):
    probe = container.get(probe_name)
    if not probe:
        return None
    http_get = probe.get("httpGet")
    return http_get.get("path") if http_get else None


def check(docs):
    problems = []

    def require(condition, message):
        if not condition:
            problems.append(message)

    # ---- Deployment：探针语义、滚动策略、终止时序、资源与安全 ----
    deployments = find(docs, "Deployment")
    require(deployments, "缺少 Deployment：这一掌的主角就是它")
    for name, deployment in deployments:
        spec = deployment["spec"]
        template = spec["template"]["spec"]
        container = container_of(deployment)
        where = "Deployment"

        require(probe_path(container, "livenessProbe") == "/actuator/health/liveness",
                f"{name}: livenessProbe 必须探 /actuator/health/liveness（只含 ping）——"
                "把依赖写进 liveness 会把外部故障放大成全集群震荡")
        require(probe_path(container, "readinessProbe") == "/actuator/health/readiness",
                f"{name}: readinessProbe 必须探 /actuator/health/readiness（含配置与数据库）")
        require(probe_path(container, "startupProbe") == "/actuator/health/readiness",
                f"{name}: 缺少 startupProbe —— 冷启动慢时应该用它，而不是放大 liveness 的 initialDelay")

        rolling = spec.get("strategy", {}).get("rollingUpdate", {})
        require(rolling.get("maxUnavailable") == 0,
                f"{name}: rollingUpdate.maxUnavailable 必须是 0（滚动不出现容量缺口）")
        require("maxSurge" in rolling,
                f"{name}: 缺 maxSurge —— maxUnavailable=0 时必须靠它多起一个副本才能滚动")

        require(template.get("terminationGracePeriodSeconds", 0) >= 60,
                f"{name}: terminationGracePeriodSeconds 应 ≥ 60，给在途长连接收尾的窗口")
        pre_stop = container.get("lifecycle", {}).get("preStop", {}).get("exec", {}).get("command")
        require(pre_stop, f"{name}: 缺 preStop —— 端点摘除的传播窗口没人填，升级会打断长连接")

        resources = container.get("resources", {})
        require(resources.get("requests", {}).get("cpu"),
                f"{name}: 缺 resources.requests.cpu —— HPA 的利用率算不出来（会一直 unknown）")
        require(resources.get("requests", {}).get("memory"), f"{name}: 缺 resources.requests.memory")

        pod_security = template.get("securityContext", {})
        require(pod_security.get("runAsNonRoot") is True, f"{name}: 必须 runAsNonRoot")
        container_security = container.get("securityContext", {})
        require(container_security.get("readOnlyRootFilesystem") is True,
                f"{name}: 应开启 readOnlyRootFilesystem")
        if container_security.get("readOnlyRootFilesystem") is True:
            mounts = {mount.get("mountPath") for mount in container.get("volumeMounts", [])}
            require("/tmp" in mounts,
                    f"{name}: 只读根文件系统必须挂出 /tmp（JVM 与临时文件要用）")
            require("/app/logs" in mounts,
                    f"{name}: 只读根文件系统必须挂出 /app/logs（容器日志目录，否则启动即失败）")

        env_from_names = {ref.get("configMapRef", {}).get("name") for ref in container.get("envFrom", [])}
        env_from_names |= {ref.get("secretRef", {}).get("name") for ref in container.get("envFrom", [])}
        require(env_from_names, f"{name}: 必须通过 envFrom 注入配置与机密（凭据不进镜像）")

    # ---- ConfigMap：记忆外置开关 ----
    configs = find(docs, "ConfigMap")
    require(configs, "缺少 ConfigMap")
    for name, config in configs:
        data = config.get("data", {})
        require(data.get("DIGITAL_HUMAN_MEMORY_STORE") == "jdbc",
                f"{name}: DIGITAL_HUMAN_MEMORY_STORE 必须是 jdbc —— "
                "内存记忆只活在 Pod 里，多副本会让用户看到 AI 失忆")
        require("DEEPSEEK_API_KEY" not in data,
                f"{name}: 凭据不能出现在 ConfigMap（那等于把密钥写进版本库）")

    # ---- Secret 模板：必需项齐备且是占位值 ----
    secrets = find(docs, "Secret")
    require(secrets, "缺少 Secret 模板")
    for name, secret in secrets:
        string_data = secret.get("stringData", {})
        for required_key in ("DEEPSEEK_API_KEY", "DIGITAL_HUMAN_DB_PASSWORD"):
            require(required_key in string_data,
                    f"{name}: 部署契约里的必需项 {required_key} 必须在 Secret 里声明")
        require(all(str(value).startswith("REPLACE_ME") for value in string_data.values()),
                f"{name}: 模板里的值必须是占位符——真值只允许由 kubectl create secret 注入")

    # ---- Ingress：SSE 的跨层契约 ----
    ingresses = find(docs, "Ingress")
    require(ingresses, "缺少 Ingress")
    for name, ingress in ingresses:
        annotations = ingress.get("metadata", {}).get("annotations", {})
        require(annotations.get("nginx.ingress.kubernetes.io/proxy-buffering") == "off",
                f"{name}: 必须关闭响应缓冲，否则逐字的流会被网关攒成一次性输出（假流式）")
        read_timeout = int(annotations.get("nginx.ingress.kubernetes.io/proxy-read-timeout", "0"))
        require(read_timeout >= 600,
                f"{name}: proxy-read-timeout 应 ≥ 600s，默认 60s 会把 Agent 的长连接掐断")

    # ---- HPA：与 Deployment 的 requests 成对存在 ----
    hpas = find(docs, "HorizontalPodAutoscaler")
    require(hpas, "缺少 HPA")
    for name, hpa in hpas:
        metrics = hpa["spec"].get("metrics", [])
        require(metrics, f"{name}: HPA 必须给出扩缩指标")
        scale_target = hpa["spec"]["scaleTargetRef"]["name"]
        require(any(doc["metadata"]["name"] == scale_target for _, doc in deployments),
                f"{name}: scaleTargetRef 指向的 Deployment 不存在：{scale_target}")
        behavior = hpa["spec"].get("behavior", {})
        require(behavior.get("scaleDown"), f"{name}: 缩容必须限速（长连接不能批量断）")

    # ---- PDB：selector 必须真的选中 Pod ----
    pdbs = find(docs, "PodDisruptionBudget")
    require(pdbs, "缺少 PodDisruptionBudget")
    for name, pdb in pdbs:
        selector = pdb["spec"].get("selector", {}).get("matchLabels", {})
        matched = False
        for _, deployment in deployments:
            labels = deployment["spec"]["template"]["metadata"].get("labels", {})
            if all(labels.get(key) == value for key, value in selector.items()):
                matched = True
        require(matched,
                f"{name}: selector 没有匹配到任何 Deployment 的 Pod 标签 —— 这时 PDB 形同虚设")

    return problems


def main():
    parser = argparse.ArgumentParser(description="校验 deploy/k8s 清单里的上线判据")
    parser.add_argument("--dir", default=DEFAULT_DIR, help="清单目录，默认 deploy/k8s")
    args = parser.parse_args()

    docs = load_documents(args.dir)
    print(f"读取清单：{len(docs)} 个对象（目录 {args.dir}）")
    for name, doc in docs:
        print(f"  - {name}: {doc.get('kind')}/{doc.get('metadata', {}).get('name')}")

    problems = check(docs)
    if problems:
        print(f"\n发现 {len(problems)} 处不满足上线判据：")
        for problem in problems:
            print(f"  ✗ {problem}")
        return 1

    print("\n全部通过：探针语义、滚动策略、终止时序、资源与安全、状态外置、SSE 跨层契约、扩缩与中断预算")
    return 0


if __name__ == "__main__":
    sys.exit(main())
