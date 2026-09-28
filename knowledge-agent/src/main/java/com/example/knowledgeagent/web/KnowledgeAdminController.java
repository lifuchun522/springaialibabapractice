package com.example.knowledgeagent.web;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.knowledgeagent.KnowledgeBase;

/**
 * 知识 Agent 自己的知识写入接口（演示用）：数据属于它，主服务看不到这张表。
 *
 * <p>不需要协议版本头——这不是 Agent 之间的协作契约，而是这个服务的运维/管理端点。
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeBase knowledgeBase;
    private final String instanceId;
    private final AtomicInteger ingestCount = new AtomicInteger();

    public KnowledgeAdminController(KnowledgeBase knowledgeBase,
                                    @Value("${a2a.instance-id:local}") String instanceId) {
        this.knowledgeBase = knowledgeBase;
        this.instanceId = instanceId;
    }

    public record DocumentRequest(String docName, String content) {
    }

    @PostMapping("/documents")
    public Map<String, Object> ingest(@RequestBody DocumentRequest request) {
        int chunks = knowledgeBase.ingest(request.docName(), request.content());
        ingestCount.addAndGet(chunks);
        return Map.of("docName", request.docName(), "chunks", chunks, "instanceId", instanceId);
    }
}
