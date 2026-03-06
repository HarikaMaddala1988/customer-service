package com.example.customerservice.exception;

/**
 * Thrown when an attempt is made to create a customer whose
 * (externalSystem, externalCustomerId) composite key already exists.
 */
public class DuplicateCustomerException extends RuntimeException {

    public DuplicateCustomerException(String externalSystem, String externalCustomerId) {
        super("Customer already exists for externalSystem=" + externalSystem
                + ", externalCustomerId=" + externalCustomerId);
    }

    public DuplicateCustomerException(String message) {
        super(message);
    }
}
