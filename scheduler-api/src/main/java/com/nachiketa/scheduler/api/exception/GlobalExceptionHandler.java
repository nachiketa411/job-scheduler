package com.nachiketa.scheduler.api.exception;

import com.nachiketa.scheduler.model.JobNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SmithyConverterException.class)
    public ResponseEntity<ErrorResponse> handleSmithyConverterException(
            SmithyConverterException exception
    ) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to process the request"
        );
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(
            JobNotFoundException exception
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            InvalidRequestException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(
                        status.value(),
                        status.getReasonPhrase(),
                        message
                ));
    }
}
