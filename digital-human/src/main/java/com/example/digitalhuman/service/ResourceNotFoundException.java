package com.example.digitalhuman.service;

/** 资源不存在，或不属于当前账号（不区分两者，避免越权探测）。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
