package com.example.customerservice.exception;

public class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException(String externalSystem, String externalCustomerId) {
        super("Customer already exists for externalSystem='%s' and externalCustomerId='%s'"
            .formatted(externalSystem, externalCustomerId));
    }
}
