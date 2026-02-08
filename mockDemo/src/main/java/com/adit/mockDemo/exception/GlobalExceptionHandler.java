package com.adit.mockDemo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(NtropiApiException.class)
    public ResponseEntity<ErrorResponse> handleNtropiFailure(NtropiApiException e){

        log.error(
                "Ntropi API failure occurred: {}",
                e.getMessage(),
                e
        );

        ErrorResponse error = new ErrorResponse("NTROPI_API_ERROR", e.getMessage());


        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericFailure(Exception e){

        log.error(
                "Unhandled exception occurred",
                e
        );

        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "Something went wrong");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

}
