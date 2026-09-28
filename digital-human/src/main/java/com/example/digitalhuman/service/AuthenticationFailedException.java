package com.example.digitalhuman.service;

/** 未登录、令牌失效或账号密码不正确。 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
