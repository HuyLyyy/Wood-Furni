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
     * Used for authentication and duplicate email check during registration.
     *
     * @param email the email address to search
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email.
     *
     * @param email the email address to check
     * @return true if a user with this email exists
     */
    boolean existsByEmail(String email);
}
