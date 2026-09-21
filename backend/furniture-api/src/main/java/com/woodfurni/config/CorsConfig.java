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
 * FIX (2026-09-21): Using setAllowedOrigins() with allowCredentials(true)
 * silently disables CORS — no Access-Control headers are added to the response.
 * The fix is to use setAllowedOriginPatterns() with wildcard patterns.
 * setAllowedOriginPatterns() with a wildcard (e.g. "https://*.vercel.app") IS
 * compatible with allowCredentials(true) and will return the request's actual
 * Origin in the Access-Control-Allow-Origin response header.
 *
 * Allows the customer-app and admin-app frontends (hosted on Vercel/Netlify/localhost)
 * to call this API directly without going through the gateway.
 */
@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
            // Local development
            "http://localhost:*",

            // Vercel deployments (production + preview)
            "https://*.vercel.app"
    );

    // -------------------------------------------------------------------------
    // Spring Security CORS — MUST be a CorsConfigurationSource bean.
    // Uses setAllowedOriginPatterns() + allowCredentials(true) which IS
    // supported by Spring and returns the actual Origin in the response header.
    // -------------------------------------------------------------------------
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
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
    // Also uses wildcard patterns for compatibility with allowCredentials(true).
    // -------------------------------------------------------------------------
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("http://localhost:*", "https://*.vercel.app")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization", "Content-Disposition")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
