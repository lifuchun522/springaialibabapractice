package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.rag.KnowledgeContractException;
import com.example.digitalhuman.rag.KnowledgeMetadata;
import com.example.digitalhuman.rag.KnowledgeVectorReloader;
import com.example.digitalhuman.repository.KnowledgeDocumentRepository;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.KnowledgeIngestService;
import com.example.digitalhuman.service.ProjectKnowledgeRetriever;
import com.example.digitalhuman.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第 8 掌四条验收标准的可验证落点：
 * <ol>
 *   <li>写入后每个 chunk 都带 projectId / docName / chunkIndex；</li>
 *   <li>任意项目检索只返回本项目 chunk；</li>
 *   <li>回答带引用，且接口返回系统渲染的 sources；</li>
 *   <li>无依据时明确拒答，且不去调用模型。</li>
 * </ol>
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-used")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagFlowTest {

    private static final String DOC_A = "faq-a.md";
    private static final String DOC_B = "faq-b.md";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private KnowledgeIngestService ingestService;

    @Autowired
    private ProjectKnowledgeRetriever retriever;

    @Autowired
    private KnowledgeDocumentRepository documents;

    @Autowired
    private AuthService authService;

    @Autowired
    private KnowledgeVectorReloader reloader;

    @Autowired
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    @MockitoBean
    private ChatModel chatModel;

    private long newProject(String title) {
        AuthService.AuthToken auth = authService.login("rag-owner", "password123");
        DigitalHumanProject project = projectService.create(auth.userId(), new ProjectService.ProjectCommand(
                "知识库演示", title, null, null, null, null, null, "你是知识库演示数字人。"));
        return project.getId();
    }

    private void ensureOwner() {
        try {
            authService.register("rag-owner", "password123");
        } catch (RuntimeException ignored) {
            // 已存在即可
        }
    }

    @Test
    @DisplayName("ingest_shouldAttachRequiredMetadataToEveryChunk")
    void ingest_shouldAttachRequiredMetadataToEveryChunk() {
        ensureOwner();
        long projectId = newProject("元数据契约");

        int chunks = ingestService.ingest(projectId, DOC_A,
                "深圳展厅的开放时间是每天九点到十八点。\n\n预约需要提前一天登记，取消请提前两小时。");

        assertThat(chunks).isGreaterThan(0);

        List<Document> hits = retriever.retrieve(projectId, "开放时间");
        assertThat(hits).isNotEmpty();
        for (Document hit : hits) {
            assertThat(hit.getMetadata().get(KnowledgeMetadata.PROJECT_ID)).isEqualTo(String.valueOf(projectId));
            assertThat(hit.getMetadata().get(KnowledgeMetadata.DOC_NAME)).isEqualTo(DOC_A);
            assertThat(hit.getMetadata().get(KnowledgeMetadata.CHUNK_INDEX)).isNotNull();
        }
    }

    @Test
    @DisplayName("ingest_shouldRejectChunkMissingMetadata")
    void ingest_shouldRejectChunkMissingMetadata() {
        // 契约校验本身：缺字段的 chunk 直接拒绝，而不是静默入库
        assertThatThrownBy(() -> KnowledgeMetadata.requireContract(List.of(
                Document.builder().text("没有元数据的一段话").build())))
                .isInstanceOf(KnowledgeContractException.class)
                .hasMessageContaining("缺少必需元数据");
    }

    @Test
    @DisplayName("retrieve_shouldNeverReturnAnotherProjectsChunks")
    void retrieve_shouldNeverReturnAnotherProjectsChunks() {
        ensureOwner();
        long projectA = newProject("A 项目");
        long projectB = newProject("B 项目");

        ingestService.ingest(projectA, DOC_A, "A 项目：深圳展厅参观免费，团体讲解需要提前三天预约。");
        ingestService.ingest(projectB, DOC_B, "B 项目：客户专享折扣为七折，服务热线是 400-000-0000。");

        // 用 B 的问题去问 A 的知识库：这是文章里那次「把 B 的报价用 A 的口吻讲出来」的事故场景
        List<Document> hits = retriever.retrieve(projectA, "客户专享折扣是多少？服务热线是多少？");

        assertThat(hits).allSatisfy(hit ->
                assertThat(hit.getMetadata().get(KnowledgeMetadata.PROJECT_ID)).isEqualTo(String.valueOf(projectA)));
        assertThat(hits).noneSatisfy(hit ->
                assertThat(String.valueOf(hit.getText())).contains("七折"));
        assertThat(hits).noneSatisfy(hit ->
                assertThat(String.valueOf(hit.getText())).contains("400-000-0000"));
    }

    @Test
    @DisplayName("retrieve_shouldRejectForeignHitsAsDefenseInDepth")
    void retrieve_shouldRejectForeignHitsAsDefenseInDepth() {
        // 命中复核：如果过滤条件被写错，这里必须喊出来，而不是把别人的内容喂给模型
        Document foreign = Document.builder()
                .text("别的项目的内容")
                .metadata(Map.of(KnowledgeMetadata.PROJECT_ID, "999999",
                        KnowledgeMetadata.DOC_NAME, "other.md",
                        KnowledgeMetadata.CHUNK_INDEX, 0))
                .build();

        assertThatThrownBy(() -> KnowledgeMetadata.requireOwnership(1L, List.of(foreign)))
                .isInstanceOf(KnowledgeContractException.class)
                .hasMessageContaining("检索命中越界");
    }

    @Test
    @DisplayName("ask_shouldReturnAnswerWithSystemRenderedSources")
    void ask_shouldReturnAnswerWithSystemRenderedSources() throws Exception {
        ensureOwner();
        long projectId = newProject("带出处回答");
        ingestService.ingest(projectId, DOC_A, "深圳展厅的开放时间是每天九点到十八点，周一闭馆。");

        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("开放时间是每天九点到十八点，周一闭馆 [1]。")))));

        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "展厅开放时间是几点？"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("[1]")))
                .andExpect(jsonPath("$.sources[0].docName").value(DOC_A))
                .andExpect(jsonPath("$.sources[0].chunkIndex").isNumber())
                .andReturn();

        // sources 是系统从命中 chunk 映射出来的，不依赖模型标注
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"sources\"");
    }

    @Test
    @DisplayName("ask_shouldRefuseWithoutCallingModelWhenKnowledgeHasNoBasis")
    void ask_shouldRefuseWithoutCallingModelWhenKnowledgeHasNoBasis() throws Exception {
        ensureOwner();
        long projectId = newProject("无依据拒答");
        ingestService.ingest(projectId, DOC_A, "深圳展厅的开放时间是每天九点到十八点。");

        org.mockito.Mockito.reset(chatModel);

        mockMvc.perform(post("/api/projects/" + projectId + "/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "量子退火在组合优化里的收敛性如何证明？"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("没有")));

        // 拒答是确定性行为：没有依据就不该请模型来编
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("delete_shouldRemoveDocumentFromVectorStoreAndSource")
    void delete_shouldRemoveDocumentFromVectorStoreAndSource() {
        ensureOwner();
        long projectId = newProject("删除文档");
        ingestService.ingest(projectId, DOC_A, "这段内容稍后会被删除：展厅停车位共有二十个。");

        assertThat(retriever.retrieve(projectId, "停车位共有多少个")).isNotEmpty();

        ingestService.delete(projectId, DOC_A);

        assertThat(documents.findByProjectIdAndDocName(projectId, DOC_A)).isEmpty();
        assertThat(retriever.retrieve(projectId, "停车位共有多少个")).isEmpty();
    }

    @Test
    @DisplayName("reloader_shouldRebuildVectorIndexFromSourceOfTruth")
    void reloader_shouldRebuildVectorIndexFromSourceOfTruth() {
        ensureOwner();
        long projectId = newProject("索引重建");
        ingestService.ingest(projectId, DOC_A, "这篇文档用来验证索引可从真源重建：展厅联系电话是 0755-00000000。");

        // 模拟「向量库是内存实现、重启即空」：清掉索引后按真源重建
        vectorStore.delete(List.of(KnowledgeMetadata.documentId(projectId, DOC_A, 0)));
        reloader.run(null);

        assertThat(documents.findByProjectIdOrderByIdAsc(projectId)).hasSize(1);
        assertThat(retriever.retrieve(projectId, "展厅联系电话是多少")).isNotEmpty();
    }
}
