package com.woodfurni.security;

import com.woodfurni.auth.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Principal object stored in SecurityContext for authenticated users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private String id;
    private String email;
    private String role;
}
