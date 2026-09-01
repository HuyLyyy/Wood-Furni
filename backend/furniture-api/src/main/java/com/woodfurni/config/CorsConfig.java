package com.woodfurni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for WOODFURNI backend.
 *
 * Allows the customer-app and admin-app frontends (hosted on Vercel/Netlify/localhost)
 * to call this API directly without going through the gateway.
 *
 * Add new deployment URLs to {@code ALLOWED_ORIGINS} below.
 */
@Configuration
public class CorsConfig {

    private static final String[] ALLOWED_ORIGINS = new String[] {
            // Local development
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:8080",

            // Vercel deployments (production + preview)
            "https://wood-furni-customer.vercel.app",
            "https://wood-furni-admin.vercel.app",
            "https://*.vercel.app"
    };

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(ALLOWED_ORIGINS)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
