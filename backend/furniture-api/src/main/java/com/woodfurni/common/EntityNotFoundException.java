package com.woodfurni.common;

/**
 * Exception thrown when an entity is not found in the database.
 */
public class EntityNotFoundException extends RuntimeException {

    private final String entityName;
    private final String fieldName;
    private final Object fieldValue;

    public EntityNotFoundException(String message) {
        super(message);
        this.entityName = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    public EntityNotFoundException(String entityName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", entityName, fieldName, fieldValue));
        this.entityName = entityName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}
