package com.example.digitalhuman.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.example.digitalhuman.domain.KnowledgeDocument;
import com.example.digitalhuman.service.AuthService;
import com.example.digitalhuman.service.KnowledgeIngestService;
import com.example.digitalhuman.service.ProjectService;
import com.example.digitalhuman.service.RagAnswerService;

/**
 * 项目知识库：写入、查看、删除与带出处的问答。
 *
 * <p>写操作要求登录与项目归属；问答接口与运行页的对话一样是公开的（终端用户在运行页上直接问）。
 */
@RestController
@RequestMapping("/api/projects/{id}")
public class RagController {

    private final AuthService authService;
    private final ProjectService projectService;
    private final KnowledgeIngestService ingestService;
    private final RagAnswerService ragAnswerService;

    public RagController(AuthService authService,
                         ProjectService projectService,
                         KnowledgeIngestService ingestService,
                         RagAnswerService ragAnswerService) {
        this.authService = authService;
        this.projectService = projectService;
        this.ingestService = ingestService;
        this.ragAnswerService = ragAnswerService;
    }

    public record DocumentRequest(String docName, String content) {
    }

    public record DocumentView(Long id, String docName, int chunkCount, String updatedAt) {

        static DocumentView of(KnowledgeDocument document) {
            return new DocumentView(document.getId(), document.getDocName(), document.getChunkCount(),
                    String.valueOf(document.getUpdatedAt()));
        }
    }

    public record AskRequest(String question) {
    }

    @PostMapping("/documents")
    public Map<String, Object> ingest(@RequestHeader("X-Token") String token,
                                      @PathVariable("id") Long projectId,
                                      @RequestBody DocumentRequest request) {
        Long ownerId = authService.requireUserId(token);
        projectService.requireOwned(ownerId, projectId);
        int chunks = ingestService.ingest(projectId, request.docName(), request.content());
        return Map.of("docName", request.docName().trim(), "chunks", chunks);
    }

    @GetMapping("/documents")
    public List<DocumentView> documents(@RequestHeader("X-Token") String token,
                                        @PathVariable("id") Long projectId) {
        Long ownerId = authService.requireUserId(token);
        projectService.requireOwned(ownerId, projectId);
        return ingestService.list(projectId).stream().map(DocumentView::of).toList();
    }

    @DeleteMapping("/documents/{docName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Token") String token,
                       @PathVariable("id") Long projectId,
                       @PathVariable("docName") String docName) {
        Long ownerId = authService.requireUserId(token);
        projectService.requireOwned(ownerId, projectId);
        ingestService.delete(projectId, docName);
    }

    /**
     * 带出处的问答：{@code sources} 由系统从命中的 chunk 映射而来，不依赖模型标注；
     * 知识库无依据时 {@code refused=true}，且不会去调用模型。
     */
    @PostMapping("/rag/ask")
    public RagAnswerService.RagAnswer ask(@PathVariable("id") Long projectId,
                                          @RequestBody AskRequest request) {
        return ragAnswerService.answer(projectId, request.question());
    }
}
