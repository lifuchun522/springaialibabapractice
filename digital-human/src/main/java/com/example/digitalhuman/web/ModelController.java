package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.digitalhuman.ai.ModelCatalog;
import com.example.digitalhuman.ai.ModelDefinition;

/**
 * 模型目录：后台下拉选择的数据源。
 *
 * <p>「有哪些模型可选」是配置，不是枚举常量——加一个模型不该改 Java 代码。
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelCatalog modelCatalog;

    public ModelController(ModelCatalog modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    /** 按 provider 分组返回，便于后台做两级下拉。 */
    @GetMapping
    public Map<String, List<ModelDefinition>> list() {
        return modelCatalog.grouped();
    }
}
