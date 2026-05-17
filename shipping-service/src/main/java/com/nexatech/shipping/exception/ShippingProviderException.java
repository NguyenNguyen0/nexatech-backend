package com.nexatech.shipping.exception;

public class ShippingProviderException extends RuntimeException {

    private final Integer statusCode;

    public ShippingProviderException(String message) {
        super(message);
        this.statusCode = null;
    }

    public ShippingProviderException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public ShippingProviderException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
