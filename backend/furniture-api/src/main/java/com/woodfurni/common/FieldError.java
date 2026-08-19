package com.woodfurni.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Field-level error detail for validation errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldError {

    private String field;
    private String message;
}
