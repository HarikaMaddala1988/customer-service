package com.example.customerservice.repository;

import com.example.customerservice.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Customer} entities.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Finds a customer by the external-system composite key.
     *
     * @param externalSystem     name of the originating system
     * @param externalCustomerId identifier within that system
     * @return an {@link Optional} containing the matched customer, or empty
     */
    Optional<Customer> findByExternalSystemAndExternalCustomerId(
            String externalSystem, String externalCustomerId);
}
