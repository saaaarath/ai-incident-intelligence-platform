package com.aiincident.orderservice.client;

public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String service, Throwable cause) {
        super(service + " is unavailable", cause);
    }
}