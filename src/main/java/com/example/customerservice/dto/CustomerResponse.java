package com.example.customerservice.dto;

import com.example.customerservice.model.Customer;
import java.time.LocalDateTime;

public record CustomerResponse(
    Long id,
    String externalSystem,
    String externalCustomerId,
    String fullName,
    String email,
    LocalDateTime createdAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getExternalSystem(),
            customer.getExternalCustomerId(),
            customer.getFullName(),
            customer.getEmail(),
            customer.getCreatedAt()
        );
    }
}
