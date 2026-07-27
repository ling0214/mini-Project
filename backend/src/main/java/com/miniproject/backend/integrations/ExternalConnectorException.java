package com.miniproject.backend.integrations;

public class ExternalConnectorException extends RuntimeException {

    public ExternalConnectorException(String message) {
        super(message);
    }

    public ExternalConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
