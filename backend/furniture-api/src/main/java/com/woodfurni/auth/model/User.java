package com.woodfurni.auth.model;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.common.BaseAuditable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * User entity for WOODFURNI platform.
 * Represents both customers and internal staff members.
 *
 * Schema (from WOODFURNI spec Mục 3.3):
 * { "_id": "ObjectId", "email": "string (unique, required)",
 *   "passwordHash": "string (required)", "fullName": "string (required)",
 *   "phone": "string", "role": "enum: CUSTOMER|SALES|WAREHOUSE|CONTENT|ADMIN",
 *   "addresses": [ ... ], "status": "enum: ACTIVE|DISABLED",
 *   "createdAt": "datetime", "updatedAt": "datetime" }
 *
 * Indexes:
 * - email: unique
 * - role: single field
 */
@Document(collection = "users")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseAuditable {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Indexed(unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    private String passwordHash;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phone;

    @Indexed(unique = true, sparse = true)
    private String customerCode;

    @NotNull(message = "Role is required")
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    @NotNull(message = "Status is required")
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    private String currentRefreshToken;
}
