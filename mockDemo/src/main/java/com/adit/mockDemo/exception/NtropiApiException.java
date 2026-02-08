package com.adit.mockDemo.exception;

public class NtropiApiException extends RuntimeException{
    public NtropiApiException(String message){
        super(message);
    }

    public NtropiApiException(String message, Throwable cause){
        super(message, cause);
    }
}
