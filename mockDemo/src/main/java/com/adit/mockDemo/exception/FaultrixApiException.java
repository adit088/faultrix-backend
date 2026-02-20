package com.adit.mockDemo.exception;

public class FaultrixApiException extends RuntimeException{
    public FaultrixApiException(String message){
        super(message);
    }

    public FaultrixApiException(String message, Throwable cause){
        super(message, cause);
    }
}
