package com.example.digitalhuman.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.example.digitalhuman.rag.KnowledgeMetadata;

/**
 * 带出处的知识问答。
 *
 * <p>三条判断落在这里：
 * <ol>
 *   <li><b>无命中就拒答，而且不调模型</b>：让模型在「没有依据」的情况下去回答，
 *       等于请它编。拒答是确定性的产品行为，不该由模型的服从性决定；</li>
 *   <li><b>来源由系统渲染</b>：{@code sources} 数组直接由命中的 Document 映射而来，
 *       不经过模型。模型可能漏标引用，接口调用方不能漏；</li>
 *   <li><b>上下文带编号与来源</b>：给模型的每一段都标 {@code [n] 来源=docName#chunkIndex}，
 *       这样它标引用时有据可依。</li>
 * </ol>
 */
@Service
public class RagAnswerService {

    private final ChatClient chatClient;
    private final ProjectKnowledgeRetriever retriever;
    private final com.example.digitalhuman.config.RagConfig.RagProperties ragProperties;
    /** 第 17 掌：检索层的观测入口。 */
    private final com.example.digitalhuman.observability.ObservedOperation observed;

    public RagAnswerService(ChatClient chatClient,
                            ProjectKnowledgeRetriever retriever,
                            com.example.digitalhuman.config.RagConfig.RagProperties ragProperties,
                            com.example.digitalhuman.observability.ObservedOperation observed) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.ragProperties = ragProperties;
        this.observed = observed;
    }

    public RagAnswer answer(Long projectId, String question) {
        // 第 17 掌：检索层同样进调用树。注意 refused（无依据拒答）在这里被分类成 RAG_EMPTY ——
        // 它是**正常业务行为**，不是故障；把它和「检索失败」混在一个指标里，看板就永远在报假警。
        return observed.observeWithOutcome("genai.rag.answer", "rag",
                java.util.Map.of("project.id", String.valueOf(projectId)),
                answer -> answer.refused() ? com.example.digitalhuman.observability.FailureType.RAG_EMPTY : null,
                () -> answerInternal(projectId, question));
    }

    private RagAnswer answerInternal(Long projectId, String question) {
        List<Document> hits = retriever.retrieve(projectId, question);
        if (hits.isEmpty()) {
            return RagAnswer.refused("这个项目的知识库里没有和该问题相关的内容，我不能凭猜测回答。"
                    + "请补充相关文档，或换一个知识库里有依据的问题。");
        }

        String context = renderContext(hits);
        String answer = chatClient.prompt()
                .system(ragProperties.systemPrompt())
                .user(userMessage(context, question))
                .call()
                .content();

        return new RagAnswer(answer, hits.stream().map(RagAnswer.Source::of).toList(), false);
    }

    private static String renderContext(List<Document> hits) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < hits.size(); index++) {
            Document hit = hits.get(index);
            builder.append('[').append(index + 1).append("] 来源=")
                    .append(hit.getMetadata().get(KnowledgeMetadata.DOC_NAME))
                    .append('#').append(hit.getMetadata().get(KnowledgeMetadata.CHUNK_INDEX))
                    .append('\n').append(hit.getText()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private static String userMessage(String context, String question) {
        return "<context>\n" + context + "\n</context>\n\n问题：" + question;
    }

    /**
     * @param refused true 表示知识库没有依据，这条回答是系统拒答而不是模型生成
     */
    public record RagAnswer(String answer, List<Source> sources, boolean refused) {

        public record Source(String docName, Integer chunkIndex, Double score, String snippet) {

            static Source of(Document document) {
                String text = document.getText();
                String snippet = text.length() <= 120 ? text : text.substring(0, 120) + "…";
                Object chunkIndex = document.getMetadata().get(KnowledgeMetadata.CHUNK_INDEX);
                return new Source(String.valueOf(document.getMetadata().get(KnowledgeMetadata.DOC_NAME)),
                        chunkIndex == null ? null : Integer.valueOf(String.valueOf(chunkIndex)),
                        document.getScore(), snippet);
            }
        }

        static RagAnswer refused(String message) {
            return new RagAnswer(message, new ArrayList<>(), true);
        }
    }
}
