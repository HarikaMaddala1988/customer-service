package com.example.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(

    @NotBlank(message = "externalSystem is required")
    String externalSystem,

    @NotBlank(message = "externalCustomerId is required")
    String externalCustomerId,

    @NotBlank(message = "fullName is required")
    String fullName,

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    String email
) {}
