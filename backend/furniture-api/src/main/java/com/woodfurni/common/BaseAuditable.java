package com.woodfurni.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Base class for all MongoDB entities.
 * Provides automatic timestamp management via Spring Data MongoDB Auditing.
 *
 * Usage: {@code @Document(collection = "products") public class Product extends BaseAuditable { ... }}
 *
 * Requirements:
 * - @EnableMongoAuditing must be present (configured in FurnitureApiApplication)
 *
 * Fields are automatically set by MongoDB auditing:
 * - createdAt: Set when entity is first persisted
 * - updatedAt: Updated on every save operation
 *
 * Note: {@code id} is {@code public} (instead of {@code protected}) so that
 * subclasses extending this class can write a custom Lombok builder method
 * that surfaces {@code .id(String)} without re-declaring the field (which
 * would cause Spring Data Mongo to detect two {@code @Id} mappings).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseAuditable {

    /**
     * MongoDB document ID.
     */
    @Id
    public String id;

    /**
     * Timestamp when the entity was first created.
     */
    @CreatedDate
    @Field("createdAt")
    public Instant createdAt;

    /**
     * Timestamp when the entity was last modified.
     */
    @LastModifiedDate
    @Field("updatedAt")
    public Instant updatedAt;
}
