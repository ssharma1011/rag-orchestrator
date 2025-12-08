package com.purchasingpower.autoflow.exception;

import lombok.Getter;

@Getter
public class BuildFailureException extends RuntimeException {

    private final String errorLogs;

    public BuildFailureException(String message, String errorLogs) {
        super(message);
        this.errorLogs = errorLogs;
    }

}
