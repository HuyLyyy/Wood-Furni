package com.woodfurni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS configuration for WOODFURNI backend.
 *
 * Provides two CORS configuration mechanisms:
 *   1. WebMvcConfigurer (addCorsMappings) — for Spring MVC request routing.
 *   2. CorsConfigurationSource bean — REQUIRED by Spring Security's built-in
 *      CORS filter. Without this bean, Spring Security's .cors() DSL call
 *      has no configuration source and all pre-flight OPTIONS requests are
 *      rejected with 403 before any controller or filter can run.
 *
 * Both configs are kept in sync with the same ALLOWED_ORIGINS list.
 *
 * Allows the customer-app and admin-app frontends (hosted on Vercel/Netlify/localhost)
 * to call this API directly without going through the gateway.
 */
@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            // Local development
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:8080",

            // Vercel deployments (production + preview)
            "https://wood-furni-customer.vercel.app",
            "https://wood-furni-admin.vercel.app",
            "https://*.vercel.app"
    );

    // -------------------------------------------------------------------------
    // Spring Security CORS — MUST be a CorsConfigurationSource bean.
    // Without this, Spring Security rejects OPTIONS pre-flight with 403 and the
    // browser reports "Invalid CORS request".
    // -------------------------------------------------------------------------
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        // Cache pre-flight response in browser for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply to every path so Spring Security can evaluate OPTIONS for /api/** too
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // -------------------------------------------------------------------------
    // Spring MVC CORS — for request routing (used alongside Security CORS).
    // The same ALLOWED_ORIGINS list is shared.
    // -------------------------------------------------------------------------
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(ALLOWED_ORIGINS.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization", "Content-Disposition")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
