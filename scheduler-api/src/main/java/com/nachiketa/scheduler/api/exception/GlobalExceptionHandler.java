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
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Unable to process the request"
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(JobNotFoundException.class)
    public  ResponseEntity<JobNotFoundException> handleJobNotFound(
            JobNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception);
    }
}
