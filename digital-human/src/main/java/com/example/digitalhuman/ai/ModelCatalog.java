package com.example.digitalhuman.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型目录：哪些 provider、每个 provider 下有哪些模型，全部来自配置。
 *
 * <p>启动期就把配置校验掉，而不是等运行页被点开才发现模型名写错——
 * 这正是文章第 06 节那条「200 但内容是空字符串」排查链的根因处置方式。
 */
@ConfigurationProperties(prefix = "digital-human")
public record ModelCatalog(List<ModelDefinition> models) {

    public ModelCatalog {
        models = models == null ? List.of() : List.copyOf(models);
    }

    @PostConstruct
    void validate() {
        if (models.isEmpty()) {
            throw new ModelRoutingException("模型目录为空：请在 digital-human.models 下配置可用的 provider 与模型");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ModelDefinition model : models) {
            if (model.id() == null || model.id().isBlank()) {
                throw new ModelRoutingException("模型目录存在空 id 的条目");
            }
            if (model.provider() == null || model.provider().isBlank()) {
                throw new ModelRoutingException("模型目录缺少 provider：" + model.id());
            }
            if (!seen.add(model.provider() + ":" + model.id())) {
                throw new ModelRoutingException("模型目录存在重复条目：" + model.provider() + ":" + model.id());
            }
        }
    }

    /** 取某个 provider+model 的定义，找不到就抛异常（调用方据此返回 400）。 */
    public ModelDefinition require(String provider, String modelId) {
        return models.stream()
                .filter(model -> model.provider().equals(provider) && model.id().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new ModelRoutingException(
                        "模型不在目录中：" + provider + "/" + modelId + "，可用：" + describe()));
    }

    public List<ModelDefinition> byProvider(String provider) {
        return models.stream().filter(model -> model.provider().equals(provider)).toList();
    }

    public Set<String> providers() {
        return models.stream().map(ModelDefinition::provider)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 按 provider 分组的只读视图，给后台下拉选择用。 */
    public Map<String, List<ModelDefinition>> grouped() {
        return models.stream().collect(Collectors.groupingBy(ModelDefinition::provider,
                java.util.LinkedHashMap::new, Collectors.toList()));
    }

    private String describe() {
        return models.stream().map(model -> model.provider() + "/" + model.id()).collect(Collectors.joining("、"));
    }
}
