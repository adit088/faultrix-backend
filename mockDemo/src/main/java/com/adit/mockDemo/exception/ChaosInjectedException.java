package com.adit.mockDemo.exception;

import com.adit.mockDemo.chaos.execution.ChaosType;
import lombok.Getter;

/**
 * Exception thrown when chaos is injected
 * This is caught by the global exception handler and converted to appropriate HTTP response
 */
@Getter
public class ChaosInjectedException extends RuntimeException {

    private final int httpStatus;
    private final ChaosType chaosType;

    public ChaosInjectedException(int httpStatus, String message, ChaosType chaosType) {
        super(message);
        this.httpStatus = httpStatus;
        this.chaosType = chaosType;
    }
}