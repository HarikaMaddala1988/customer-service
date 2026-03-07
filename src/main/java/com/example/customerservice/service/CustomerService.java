package com.example.customerservice.service;

import com.example.customerservice.dto.CreateCustomerRequest;
import com.example.customerservice.dto.UpdateCustomerRequest;
import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.exception.DuplicateCustomerException;
import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public Customer createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer: externalSystem={}, externalCustomerId={}", request.externalSystem(), request.externalCustomerId());
        customerRepository.findByExternalSystemAndExternalCustomerId(
                request.externalSystem(), request.externalCustomerId())
                .ifPresent(c -> {
                    log.warn("Duplicate customer found: externalSystem={}, externalCustomerId={}", request.externalSystem(), request.externalCustomerId());
                    throw new DuplicateCustomerException(request.externalSystem(), request.externalCustomerId());
                });

        Customer customer = new Customer();
        customer.setExternalSystem(request.externalSystem());
        customer.setExternalCustomerId(request.externalCustomerId());
        customer.setFullName(request.fullName());
        customer.setEmail(request.email());
        Customer saved = customerRepository.save(customer);
        log.info("Customer created successfully: id={}", saved.getId());
        return saved;
    }

    public Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public Customer updateCustomer(Long id, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        return customerRepository.save(customer);
    }
}
