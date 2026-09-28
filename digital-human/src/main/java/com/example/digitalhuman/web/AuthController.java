package com.example.digitalhuman.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.digitalhuman.domain.User;
import com.example.digitalhuman.service.AuthService;

/** 注册、登录、登出。登录成功后返回的 token 通过 X-Token 头携带。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record AuthRequest(String username, String password) {
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody AuthRequest request) {
        User user = authService.register(request.username(), request.password());
        return Map.of("id", user.getId(), "username", user.getUsername());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AuthRequest request) {
        AuthService.AuthToken token = authService.login(request.username(), request.password());
        return Map.of("token", token.token(), "userId", token.userId(), "username", token.username());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "X-Token", required = false) String token) {
        authService.logout(token);
    }
}
