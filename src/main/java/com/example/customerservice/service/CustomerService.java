package com.example.customerservice.service;

import com.example.customerservice.dto.CreateCustomerRequest;
import com.example.customerservice.dto.UpdateCustomerRequest;
import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.exception.DuplicateCustomerException;
import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Customer createCustomer(CreateCustomerRequest request) {
        customerRepository.findByExternalSystemAndExternalCustomerId(
                request.externalSystem(), request.externalCustomerId())
                .ifPresent(c -> {
                    throw new DuplicateCustomerException(request.externalSystem(), request.externalCustomerId());
                });

        Customer customer = new Customer();
        customer.setExternalSystem(request.externalSystem());
        customer.setExternalCustomerId(request.externalCustomerId());
        customer.setFullName(request.fullName());
        customer.setEmail(request.email());
        return customerRepository.save(customer);
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
