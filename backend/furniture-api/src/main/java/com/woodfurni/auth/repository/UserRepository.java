package com.woodfurni.auth.repository;

import com.woodfurni.auth.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for User entity.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Find a user by their email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find a user by email containing (case-insensitive).
     */
    Optional<User> findByEmailContainingIgnoreCase(String email);

    /**
     * Find a user by their customer code.
     */
    Optional<User> findByCustomerCode(String customerCode);

    /**
     * Check if a user exists with the given email.
     */
    boolean existsByEmail(String email);
}