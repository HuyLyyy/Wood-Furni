package com.woodfurni.auth.model;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User entity.
 */
class UserTest {

    @Test
    void builder_shouldCreateUserWithDefaultRoleAndStatus() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedpassword")
                .fullName("Test User")
                .build();

        assertEquals("test@example.com", user.getEmail());
        assertEquals("hashedpassword", user.getPasswordHash());
        assertEquals("Test User", user.getFullName());
        assertEquals(Role.CUSTOMER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void builder_shouldCreateUserWithAllFields() {
        Address address = Address.builder()
                .id("addr-1")
                .label("Home")
                .line1("123 Main Street")
                .ward("Ward 1")
                .district("District 1")
                .city("Ho Chi Minh City")
                .isDefault(true)
                .build();

        User user = User.builder()
                .email("admin@woodfurni.com")
                .passwordHash("adminhash")
                .fullName("Admin User")
                .phone("0909123456")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .addresses(List.of(address))
                .currentRefreshToken("refresh-token-123")
                .build();

        assertEquals("admin@woodfurni.com", user.getEmail());
        assertEquals("adminhash", user.getPasswordHash());
        assertEquals("Admin User", user.getFullName());
        assertEquals("0909123456", user.getPhone());
        assertEquals(Role.ADMIN, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(1, user.getAddresses().size());
        assertEquals("Home", user.getAddresses().get(0).getLabel());
        assertTrue(user.getAddresses().get(0).isDefault());
        assertEquals("refresh-token-123", user.getCurrentRefreshToken());
    }

    @Test
    void address_shouldBuildWithDefaults() {
        Address address = Address.builder()
                .line1("456 Oak Avenue")
                .city("Hanoi")
                .build();

        assertNull(address.getId());
        assertNull(address.getLabel());
        assertEquals("456 Oak Avenue", address.getLine1());
        assertEquals("Hanoi", address.getCity());
        assertFalse(address.isDefault());
    }

    @Test
    void address_isDefaultShouldDefaultToFalse() {
        Address address = new Address();
        assertFalse(address.isDefault());
    }

    @Test
    void user_addressesShouldDefaultToEmptyList() {
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("hash")
                .fullName("Test")
                .build();

        assertNotNull(user.getAddresses());
        assertTrue(user.getAddresses().isEmpty());
    }
}
