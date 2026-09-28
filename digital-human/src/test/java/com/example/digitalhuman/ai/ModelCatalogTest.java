package com.example.digitalhuman.ai;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 模型目录的启动期校验：配置错了就早点炸，不要等运行页被点开。 */
class ModelCatalogTest {

    private static ModelDefinition model(String id, String provider) {
        return new ModelDefinition(id, provider, id, "测试用");
    }

    @Test
    @DisplayName("validate_shouldRejectDuplicateEntries")
    void validate_shouldRejectDuplicateEntries() {
        ModelCatalog catalog = new ModelCatalog(List.of(
                model("deepseek-flash", "deepseek"), model("deepseek-flash", "deepseek")));

        assertThatThrownBy(catalog::validate)
                .isInstanceOf(ModelRoutingException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("validate_shouldRejectBlankProviderOrEmptyCatalog")
    void validate_shouldRejectBlankProviderOrEmptyCatalog() {
        assertThatThrownBy(() -> new ModelCatalog(List.of(model("m1", " "))).validate())
                .isInstanceOf(ModelRoutingException.class)
                .hasMessageContaining("provider");

        assertThatThrownBy(() -> new ModelCatalog(List.of()).validate())
                .isInstanceOf(ModelRoutingException.class)
                .hasMessageContaining("为空");
    }

    @Test
    @DisplayName("require_shouldFindModel_andRejectUnknownOne")
    void require_shouldFindModel_andRejectUnknownOne() {
        ModelCatalog catalog = new ModelCatalog(List.of(
                model("deepseek-flash", "deepseek"), model("deepseek-v4-pro", "deepseek")));

        assertThat(catalog.require("deepseek", "deepseek-v4-pro").id()).isEqualTo("deepseek-v4-pro");
        assertThat(catalog.grouped()).containsOnlyKeys("deepseek");
        assertThat(catalog.byProvider("deepseek")).hasSize(2);

        assertThatThrownBy(() -> catalog.require("deepseek", "gpt-9"))
                .isInstanceOf(ModelRoutingException.class)
                .hasMessageContaining("不在目录中")
                .hasMessageContaining("deepseek/gpt-9");
    }
}
