package com.example.digitalhuman.service;

/** 用户名已存在。 */
public class UsernameExistsException extends RuntimeException {

    public UsernameExistsException(String username) {
        super("用户名已存在：" + username);
    }
}
