package com.example.customerservice.service;

import com.example.customerservice.dto.CreateCustomerRequest;
import com.example.customerservice.dto.CustomerResponse;
import com.example.customerservice.dto.UpdateCustomerRequest;
import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.exception.DuplicateCustomerException;
import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer existingCustomer;

    @BeforeEach
    void setUp() {
        existingCustomer = new Customer();
        existingCustomer.setId(1L);
        existingCustomer.setFullName("Alice Smith");
        existingCustomer.setEmail("alice@example.com");
    }

    // ── createCustomer ───────────────────────────────────────────────────────

    @Test
    void createCustomer_success_savesAndReturnsCustomer() {
        when(customerRepository.findByExternalSystemAndExternalCustomerId("SYS", "C1"))
                .thenReturn(java.util.Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCustomerRequest req = new CreateCustomerRequest("SYS", "C1", "Alice Smith", "alice@example.com");
        Customer created = customerService.createCustomer(req);

        assertThat(created.getFullName()).isEqualTo("Alice Smith");
        assertThat(created.getEmail()).isEqualTo("alice@example.com");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void createCustomer_duplicate_throwsException() {
        when(customerRepository.findByExternalSystemAndExternalCustomerId("SYS", "C1"))
                .thenReturn(java.util.Optional.of(existingCustomer));

        CreateCustomerRequest req = new CreateCustomerRequest("SYS", "C1", "Alice Smith", "alice@example.com");

        assertThatThrownBy(() -> customerService.createCustomer(req))
                .isInstanceOf(DuplicateCustomerException.class);
        verify(customerRepository, never()).save(any());
    }

    // ── getCustomer ──────────────────────────────────────────────────────────

    @Test
    void getCustomer_success_returnsCustomer() {
        when(customerRepository.findById(1L)).thenReturn(java.util.Optional.of(existingCustomer));

        Customer found = customerService.getCustomer(1L);

        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getFullName()).isEqualTo("Alice Smith");
    }

    @Test
    void getCustomer_notFound_throwsException() {
        when(customerRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(99L))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void customerResponse_from_mapsAllFields() {
        existingCustomer.setExternalSystem("SYS");
        existingCustomer.setExternalCustomerId("C1");

        CustomerResponse resp = CustomerResponse.from(existingCustomer);

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.fullName()).isEqualTo("Alice Smith");
        assertThat(resp.email()).isEqualTo("alice@example.com");
        assertThat(resp.externalSystem()).isEqualTo("SYS");
    }

    // ── updateCustomer ────────────────────────────────────────────────────────

    @Test
    void updateCustomer_success_updatesAllFields() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Alice Johnson");
        req.setEmail("alice.johnson@example.com");

        Customer updated = customerService.updateCustomer(1L, req);

        assertThat(updated.getFullName()).isEqualTo("Alice Johnson");
        assertThat(updated.getEmail()).isEqualTo("alice.johnson@example.com");
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    void updateCustomer_nullEmail_clearsEmail() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Alice Johnson");
        req.setEmail(null);

        Customer updated = customerService.updateCustomer(1L, req);

        assertThat(updated.getFullName()).isEqualTo("Alice Johnson");
        assertThat(updated.getEmail()).isNull();
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    void updateCustomer_customerNotFound_throwsException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Ghost");

        assertThatThrownBy(() -> customerService.updateCustomer(99L, req))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("99");

        verify(customerRepository, never()).save(any());
    }
}
