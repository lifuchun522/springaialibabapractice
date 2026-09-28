package com.example.digitalhuman.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.digitalhuman.ai.ModelInvocationException;
import com.example.digitalhuman.ai.ModelRoutingException;
import com.example.digitalhuman.service.AuthenticationFailedException;
import com.example.digitalhuman.service.ResourceNotFoundException;
import com.example.digitalhuman.service.UsernameExistsException;

/** 统一把服务层异常翻译成 HTTP 语义，控制器里不再散落 if-else。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UsernameExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(UsernameExistsException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleUnauthorized(AuthenticationFailedException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(ResourceNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    /** 模型不在目录里 = 配置错误，直接 400，不要等调用时才炸。 */
    @ExceptionHandler(ModelRoutingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleRouting(ModelRoutingException ex) {
        return Map.of("error", ex.getMessage(), "type", "MODEL_NOT_CONFIGURED");
    }

    /**
     * 模型调用失败：三类错误各自有确定的响应体，不会退化成默认 500 页面。
     *
     * <p>响应体形状固定：{@code {error, type, provider, model}}，
     * 上游是谁、用的哪个模型、失败在哪一类，排查时不用再去翻日志猜。
     */
    @ExceptionHandler(ModelInvocationException.class)
    public ResponseEntity<Map<String, Object>> handleInvocation(ModelInvocationException ex) {
        HttpStatus status = switch (ex.kind()) {
            case AUTH, PROVIDER_ERROR, EMPTY_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "MODEL_" + ex.kind().name());
        body.put("provider", ex.provider());
        body.put("model", ex.model());
        return ResponseEntity.status(status).body(body);
    }
}
