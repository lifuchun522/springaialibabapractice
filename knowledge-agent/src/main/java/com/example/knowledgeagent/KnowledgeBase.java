package com.example.knowledgeagent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.example.knowledgeagent.domain.KnowledgeDocument;
import com.example.knowledgeagent.repository.KnowledgeDocumentRepository;

/**
 * 知识 Agent 自己的知识：**数据属于它，主服务看不到这张表**。
 *
 * <p>这是「跨服务」最容易被糊过去的一步：如果知识 Agent 还去读主服务的库，
 * 那拆出去的只是一个 HTTP 壳子，生命周期并没有解耦。
 *
 * <p>检索刻意做得简单（词法重叠打分）：这一掌要证明的是协议、发现与失败语义，
 * 不是检索质量——把检索做强会让读者以为「跨服务的收益来自检索变好」。
 */
@Component
public class KnowledgeBase {

    private final KnowledgeDocumentRepository documents;

    public KnowledgeBase(KnowledgeDocumentRepository documents) {
        this.documents = documents;
    }

    public int ingest(String docName, String content) {
        documents.save(new KnowledgeDocument(docName.trim(), content));
        return 1;
    }

    public List<KnowledgeDocument> list() {
        return documents.findAll();
    }

    /** 词法打分：命中字数 / 查询长度，稳定且可解释（不引入 embedding 依赖）。 */
    public List<Map<String, String>> search(String question, int topK) {
        String query = question == null ? "" : question;
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeDocument document : documents.findAll()) {
            double score = overlap(query, document.getContent());
            if (score > 0) {
                scored.add(new Scored(document, score));
            }
        }
        scored.sort(Comparator.comparingDouble((Scored item) -> item.score).reversed());
        List<Map<String, String>> hits = new ArrayList<>();
        for (Scored item : scored.stream().limit(topK).toList()) {
            Map<String, String> hit = new LinkedHashMap<>();
            hit.put("docName", item.document.getDocName());
            hit.put("chunkIndex", "0");
            hit.put("score", String.valueOf(Math.round(item.score * 1000) / 1000.0));
            hit.put("text", item.document.getContent());
            hits.add(hit);
        }
        return hits;
    }

    private static double overlap(String query, String content) {
        if (query.isBlank() || content == null) {
            return 0;
        }
        int hit = 0;
        for (int i = 0; i < query.length(); i++) {
            if (content.indexOf(query.charAt(i)) >= 0) {
                hit++;
            }
        }
        return (double) hit / query.length();
    }

    private record Scored(KnowledgeDocument document, double score) {
    }

    /** 任务状态机：内存实现，进程重启即空（遗留问题里如实写着）。 */
    @Component
    public static class TaskStore {

        private final Map<String, A2AProtocol.Task> tasks = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        public A2AProtocol.Task create(String traceId, String question) {
            String taskId = "task-" + sequence.incrementAndGet() + "-" + traceId;
            long now = System.currentTimeMillis();
            A2AProtocol.Task task = new A2AProtocol.Task(taskId, traceId,
                    A2AProtocol.TaskState.SUBMITTED, question, null, List.of(), null, now, now);
            tasks.put(taskId, task);
            return task;
        }

        public Optional<A2AProtocol.Task> get(String taskId) {
            return Optional.ofNullable(tasks.get(taskId));
        }

        public A2AProtocol.Task update(String taskId, A2AProtocol.TaskState state, String answer,
                                       List<Map<String, String>> artifacts, String error) {
            A2AProtocol.Task current = tasks.get(taskId);
            if (current == null) {
                throw new IllegalArgumentException("任务不存在：" + taskId);
            }
            A2AProtocol.Task updated = new A2AProtocol.Task(current.taskId(), current.traceId(), state,
                    current.question(), answer == null ? current.answer() : answer,
                    artifacts == null ? current.artifacts() : artifacts, error,
                    current.createdAt(), System.currentTimeMillis());
            tasks.put(taskId, updated);
            return updated;
        }

        public boolean cancel(String taskId) {
            A2AProtocol.Task current = tasks.get(taskId);
            if (current == null) {
                return false;
            }
            update(taskId, A2AProtocol.TaskState.CANCELED, current.answer(), current.artifacts(), null);
            return true;
        }
    }
}
