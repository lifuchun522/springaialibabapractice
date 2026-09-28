package com.example.digitalhuman.service;

/** 确认令牌无效、已使用或不属于该项目。 */
public class ConfirmationRejectedException extends RuntimeException {

    public ConfirmationRejectedException(String message) {
        super(message);
    }
}
