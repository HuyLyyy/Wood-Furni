package com.woodfurni.auth.repository;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.auth.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserRepository.
 * Uses @DataMongoTest for embedded MongoDB testing.
 */
@DataMongoTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    void save_shouldPersistUser() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedpassword")
                .fullName("Test User")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
        assertEquals("test@example.com", saved.getEmail());
        assertEquals("Test User", saved.getFullName());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findByEmail_shouldReturnUser_whenExists() {
        User user = User.builder()
                .email("findme@example.com")
                .passwordHash("hash")
                .fullName("Find Me")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("findme@example.com");

        assertTrue(found.isPresent());
        assertEquals("Find Me", found.get().getFullName());
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenNotExists() {
        Optional<User> found = userRepository.findByEmail("notfound@example.com");
        assertFalse(found.isPresent());
    }

    @Test
    void existsByEmail_shouldReturnTrue_whenExists() {
        User user = User.builder()
                .email("exists@example.com")
                .passwordHash("hash")
                .fullName("Exists")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        assertTrue(userRepository.existsByEmail("exists@example.com"));
        assertFalse(userRepository.existsByEmail("notexists@example.com"));
    }

    @Test
    void save_shouldEnforceUniqueEmailIndex() {
        User user1 = User.builder()
                .email("unique@test.com")
                .passwordHash("hash1")
                .fullName("User 1")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user1);

        User user2 = User.builder()
                .email("unique@test.com")
                .passwordHash("hash2")
                .fullName("User 2")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> userRepository.save(user2)
        );
    }
}
