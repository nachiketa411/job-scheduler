package com.nachiketa.scheduler.api.exception;

public record ErrorResponse(
        int status,
        String error,
        String message
) {}
