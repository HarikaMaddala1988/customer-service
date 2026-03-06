package com.example.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateCustomerRequest {

    @NotBlank(message = "fullName must not be blank")
    @Size(max = 255, message = "fullName must not exceed 255 characters")
    private String fullName;

    @Email(message = "email must be a valid email address")
    @Size(max = 255, message = "email must not exceed 255 characters")
    private String email;

    public UpdateCustomerRequest() {}

    public UpdateCustomerRequest(String fullName, String email) {
        this.fullName = fullName;
        this.email   = email;
    }

    public String getFullName() { return fullName; }
    public void   setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void   setEmail(String email) { this.email = email; }
}
