package com.example.digitalhuman.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/** 词法向量化：确定性、归一化、字面重叠越多越相似（它不理解同义，这一点必须知道）。 */
class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    private static double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return dot;   // 两侧都已 L2 归一化，点积即余弦
    }

    @Test
    @DisplayName("embed_shouldBeDeterministicAndNormalized")
    void embed_shouldBeDeterministicAndNormalized() {
        float[] first = model.embed("深圳展厅的预约开放时间是每天九点到十八点");
        float[] second = model.embed("深圳展厅的预约开放时间是每天九点到十八点");

        assertThat(first).hasSize(HashingEmbeddingModel.DIMENSIONS);
        assertThat(first).isEqualTo(second);

        double norm = 0;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("embed_shouldRankLexicalOverlapHigherThanUnrelatedText")
    void embed_shouldRankLexicalOverlapHigherThanUnrelatedText() {
        float[] question = model.embed("展厅的预约开放时间是几点");
        float[] related = model.embed("深圳展厅的预约开放时间是每天九点到十八点，节假日照常。");
        float[] unrelated = model.embed("本季度财报显示毛利率提升了三个百分点。");

        assertThat(cosine(question, related)).isGreaterThan(cosine(question, unrelated));
    }

    @Test
    @DisplayName("embed_shouldGiveNearZeroSimilarityForNoOverlapAtAll")
    void embed_shouldGiveNearZeroSimilarityForNoOverlapAtAll() {
        // 「无依据拒答」这条产品行为就靠这个性质：字面完全不重叠时相似度接近 0
        float[] question = model.embed("请问你们支持哪些支付方式");
        float[] unrelated = model.embed("量子退火在组合优化里的一种启发式近似算法");

        assertThat(cosine(question, unrelated)).isLessThan(0.01);
    }

    @Test
    @DisplayName("embed_shouldAcceptDocumentAsWellAsText")
    void embed_shouldAcceptDocumentAsWellAsText() {
        Document document = Document.builder().text("展厅预约需要提前一天登记").build();

        assertThat(model.embed(document)).isEqualTo(model.embed("展厅预约需要提前一天登记"));
        assertThat(model.dimensions()).isEqualTo(HashingEmbeddingModel.DIMENSIONS);
    }

    @Test
    @DisplayName("embed_shouldReturnZeroVectorForBlankText")
    void embed_shouldReturnZeroVectorForBlankText() {
        float[] vector = model.embed("   ");

        assertThat(vector).hasSize(HashingEmbeddingModel.DIMENSIONS);
        assertThat(vector).containsOnly(0.0f);
    }
}
