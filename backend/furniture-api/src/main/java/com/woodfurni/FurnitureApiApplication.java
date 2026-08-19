package com.woodfurni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * WOODFURNI - Furniture E-Commerce API
 *
 * Main Spring Boot Application Entry Point.
 * This is a Modular Monolith architecture for the WOODFURNI
 * e-commerce platform specializing in wooden furniture.
 *
 * Tech Stack:
 * - Spring Boot 3.2.x (Java 17+)
 * - Spring Data MongoDB
 * - Spring Security with JWT
 * - SpringDoc OpenAPI (Swagger UI)
 *
 * API Base Path: /api/v1
 */
@SpringBootApplication
@EnableMongoAuditing
public class FurnitureApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FurnitureApiApplication.class, args);
    }
}
