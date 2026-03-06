package com.example.customerservice.controller;

import com.example.customerservice.dto.CreateCustomerRequest;
import com.example.customerservice.dto.UpdateCustomerRequest;
import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.exception.DuplicateCustomerException;
import com.example.customerservice.model.Customer;
import com.example.customerservice.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    // ── GET /api/customers/{id} ───────────────────────────────────────────────
    @Test
    void getCustomer_returnsOkAndCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Jane Doe");
        customer.setEmail("jane@example.com");

        when(customerService.getCustomer(1L)).thenReturn(customer);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void getCustomer_notFound_returns404() throws Exception {
        when(customerService.getCustomer(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/customers ───────────────────────────────────────────────────
    @Test
    void createCustomer_returnsCreatedAndCustomer() throws Exception {
        CreateCustomerRequest req = new CreateCustomerRequest("SYS", "C1", "Jane Doe", "jane@example.com");

        Customer created = new Customer();
        created.setId(1L);
        created.setExternalSystem("SYS");
        created.setExternalCustomerId("C1");
        created.setFullName("Jane Doe");
        created.setEmail("jane@example.com");

        when(customerService.createCustomer(any(CreateCustomerRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void createCustomer_duplicate_returns409() throws Exception {
        CreateCustomerRequest req = new CreateCustomerRequest("SYS", "C1", "Jane Doe", "jane@example.com");

        when(customerService.createCustomer(any(CreateCustomerRequest.class)))
                .thenThrow(new DuplicateCustomerException("SYS", "C1"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ── PUT /api/customers/{id} – happy path ──────────────────────────────────
    @Test
    void updateCustomer_returnsOkAndUpdatedCustomer() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Jane Doe");
        req.setEmail("jane@example.com");

        Customer updated = new Customer();
        updated.setId(1L);
        updated.setFullName("Jane Doe");
        updated.setEmail("jane@example.com");

        when(customerService.updateCustomer(eq(1L), any(UpdateCustomerRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    // ── PUT /customers/{id} – customer not found ──────────────────────────────
    @Test
    void updateCustomer_notFound_returns404() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Jane Doe");
        req.setEmail("jane@example.com");

        when(customerService.updateCustomer(eq(99L), any(UpdateCustomerRequest.class)))
                .thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(put("/api/customers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── PUT /customers/{id} – blank fullName fails validation ─────────────────
    @Test
    void updateCustomer_blankFullName_returns400() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("");
        req.setEmail("jane@example.com");

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /customers/{id} – invalid email fails validation ──────────────────
    @Test
    void updateCustomer_invalidEmail_returns400() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Jane Doe");
        req.setEmail("not-an-email");

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
