package com.woodfurni.config;

import com.woodfurni.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for WOODFURNI API.
 *
 * Phân quyền chi tiết được khai báo tại từng Controller qua @PreAuthorize.
 * SecurityFilterChain chỉ khai báo public endpoints.
 *
 * RBAC Role Constraints (ánh xạ theo WOODFURNI_AI_DEV_SPEC_1.md):
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Role       │ Modules                                                    │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ CUSTOMER   │ cart, checkout (M05/M06), review (M09), xem đơn của mình   │
 * │ SALES      │ dashboard (M10), xem/đổi trạng thái order (M06)             │
 * │ WAREHOUSE  │ quản lý inventory (M07)                                    │
 * │ CONTENT    │ quản lý category/material/product (M02/M03)              │
 * │ ADMIN      │ toàn quyền + dashboard (M10)                              │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Public endpoints: /api/v1/auth/**, GET /api/v1/products/**, GET /api/v1/categories/**,
 *                    GET /api/v1/materials/**, Swagger, Actuator
 * Protected endpoints: tất cả còn lại → require authentication + @PreAuthorize tại Controller
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for REST API (stateless JWT)
                .csrf(csrf -> csrf.disable())

                // CORS enabled for direct frontend access (Vercel, localhost).
                // CorsConfig defines the allowed origins.
                .cors(cors -> {})

                // Stateless session management
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // ========== CORS preflight ==========
                        // Browsers send an OPTIONS request before cross-origin
                        // GET/POST/PUT/DELETE. Without this permitAll, Spring
                        // Security returns 403 on OPTIONS and the browser reports
                        // "Invalid CORS request". (WebMvcConfigurer and
                        // CorsConfigurationSource alone are not sufficient.)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ========== Public Endpoints ==========
                        // Auth endpoints — match the full servlet path because
                        // DispatcherServlet is mapped to /api/v1, so the servlet
                        // path for /api/v1/auth/login is /api/v1/auth/login.
                        // Using mvcMatchers() would auto-handle this but requires
                        // spring-security-config. Using the explicit path here is
                        // clearer and equivalent.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Also keep the short form for direct gateway calls.
                        .requestMatchers("/auth/**").permitAll()

                        // Catalog - public read-only (protected endpoints use @PreAuthorize per Controller)
                        .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/materials/**").permitAll()

                        // Storage - serve uploaded product images publicly
                        .requestMatchers("/storage/**").permitAll()

                        // Shipping distances — public list (admin-only mutations use @PreAuthorize per Controller)
                        .requestMatchers(HttpMethod.GET, "/shipping/distances").permitAll()

                        // Reviews - public read-only (POST requires CUSTOMER)
                        .requestMatchers(HttpMethod.GET, "/products/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/*/reviews/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/reviews").permitAll()

                        // Swagger / OpenAPI
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Health checks
                        .requestMatchers("/health/**", "/actuator/health").permitAll()

                        // ========== Protected Endpoints ==========
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
