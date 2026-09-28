package com.example.digitalhuman.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.digitalhuman.domain.User;
import com.example.digitalhuman.repository.UserRepository;

/**
 * 注册与登录。
 *
 * <p>密码用 BCrypt 存哈希；令牌放在进程内存里——**仅用于 Demo**，重启即失效，生产必须换成正式认证方案。
 * 这一点写在代码里，也写在验收记录里，不假装它已经是生产方案。
 */
@Service
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User register(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("用户名不能为空，且密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (users.findByUsername(name).isPresent()) {
            throw new UsernameExistsException(name);
        }
        return users.save(new User(name, passwordEncoder.encode(password), name));
    }

    @Transactional(readOnly = true)
    public AuthToken login(String username, String password) {
        String name = username == null ? "" : username.trim();
        User user = users.findByUsername(name)
                .filter(candidate -> password != null && passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new AuthenticationFailedException("用户名或密码错误"));

        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, user.getId());
        return new AuthToken(token, user.getId(), user.getUsername());
    }

    public void logout(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** 已登录则返回 userId，否则抛 401 语义的异常。 */
    public Long requireUserId(String token) {
        Long userId = token == null ? null : tokens.get(token);
        if (userId == null) {
            throw new AuthenticationFailedException("未登录或令牌已失效");
        }
        return userId;
    }

    public record AuthToken(String token, Long userId, String username) {
    }
}
