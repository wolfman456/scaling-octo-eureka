package com.wood.worker.service;

public class AiCallException extends RuntimeException {

    public AiCallException(String message) {
        super(message);
    }

    public AiCallException(String message, Throwable cause) {
        super(message, cause);
    }
}