package com.woodfurni.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Base class for all MongoDB entities.
 * Provides automatic timestamp management via Spring Data MongoDB Auditing.
 *
 * Usage: Entity classes should extend this class:
 * {@code
 * @Document(collection = "products")
 * public class Product extends BaseAuditable {
 *     // ...
 * }
 * }
 *
 * Requirements:
 * - @EnableMongoAuditing must be present (configured in FurnitureApiApplication)
 * - Entity must extend BaseAuditable
 *
 * Fields are automatically set by MongoDB auditing:
 * - createdAt: Set when entity is first persisted
 * - updatedAt: Updated on every save operation
 */
@Getter
@Setter
public abstract class BaseAuditable {

    /**
     * MongoDB document ID.
     * Subclasses can override this field name with @Field if needed.
     */
    @Id
    protected String id;

    /**
     * Timestamp when the entity was first created.
     * Automatically set by @CreatedDate when the entity is saved for the first time.
     */
    @CreatedDate
    @Field("createdAt")
    protected Instant createdAt;

    /**
     * Timestamp when the entity was last modified.
     * Automatically updated by @LastModifiedDate on every save operation.
     */
    @LastModifiedDate
    @Field("updatedAt")
    protected Instant updatedAt;
}
