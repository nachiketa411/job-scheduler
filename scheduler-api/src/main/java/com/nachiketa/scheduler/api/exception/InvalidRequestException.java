package com.nachiketa.scheduler.api.exception;

public class InvalidRequestException extends IllegalArgumentException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
