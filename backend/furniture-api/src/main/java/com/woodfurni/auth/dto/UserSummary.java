package com.woodfurni.auth.dto;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary view of a user (for embedding in responses).
 * Excludes sensitive fields like passwordHash and tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {

    private String id;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private UserStatus status;
}
