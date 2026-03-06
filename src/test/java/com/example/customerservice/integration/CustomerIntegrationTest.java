package com.example.customerservice.integration;

import com.example.customerservice.dto.UpdateCustomerRequest;
import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        Customer c = new Customer();
        c.setExternalSystem("TEST");
        c.setExternalCustomerId("INT-001");
        c.setFullName("Integration User");
        c.setEmail("integration@example.com");
        savedCustomer = customerRepository.save(c);
    }

    @Test
    void updateCustomer_success() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Updated Integration User");
        req.setEmail("updated.integration@example.com");

        mockMvc.perform(put("/api/customers/" + savedCustomer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Integration User"))
                .andExpect(jsonPath("$.email").value("updated.integration@example.com"));
    }

    @Test
    void updateCustomer_notFound_returns404() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Ghost User");
        req.setEmail("ghost@example.com");

        mockMvc.perform(put("/api/customers/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCustomer_blankName_returns400() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("");
        req.setEmail("valid@example.com");

        mockMvc.perform(put("/api/customers/" + savedCustomer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCustomer_invalidEmail_returns400() throws Exception {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setFullName("Valid Name");
        req.setEmail("not-an-email");

        mockMvc.perform(put("/api/customers/" + savedCustomer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
