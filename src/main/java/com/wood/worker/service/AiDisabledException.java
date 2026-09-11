package com.wood.worker.service;

public class AiDisabledException extends RuntimeException {

    public AiDisabledException(String message) {
        super(message);
    }
}