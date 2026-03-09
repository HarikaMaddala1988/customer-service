package com.example.customerservice.controller;

import com.example.customerservice.dto.CreateCustomerRequest;
import com.example.customerservice.dto.HelloResponse;
import com.example.customerservice.dto.UpdateCustomerRequest;
import com.example.customerservice.model.Customer;
import com.example.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // ── GET /api/hello ────────────────────────────────────────────────────────
    @GetMapping("/api/hello")
    public ResponseEntity<HelloResponse> hello() {
        return ResponseEntity.ok(new HelloResponse("Hello from Customer Service"));
    }

    // ── GET /api/customers/{id} ───────────────────────────────────────────────
    @GetMapping("/api/customers/{id}")
    public ResponseEntity<Customer> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    // ── POST /api/customers ───────────────────────────────────────────────────
    @PostMapping("/api/customers")
    public ResponseEntity<Customer> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(201).body(customerService.createCustomer(request));
    }

    // ── PUT /api/customers/{id} ───────────────────────────────────────────────
    @PutMapping("/api/customers/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }
}
