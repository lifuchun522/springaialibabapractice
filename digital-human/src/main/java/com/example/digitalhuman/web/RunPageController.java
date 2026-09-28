package com.example.digitalhuman.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行页：{@code /run/{projectId}}。
 *
 * <p>页面本身是静态的，配置由前端再调 {@code /api/projects/{id}/runtime} 拉取——
 * 这样运营改开场白、主题色，刷新页面即可生效，不需要重新发版。
 */
@RestController
public class RunPageController {

    private static final String TEMPLATE = "static/run.html";
    private static final String PROJECT_ID_PLACEHOLDER = "__PROJECT_ID__";

    @GetMapping(value = "/run/{projectId}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String runPage(@PathVariable Long projectId) throws IOException {
        try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return html.replace(PROJECT_ID_PLACEHOLDER, String.valueOf(projectId));
        }
    }
}
