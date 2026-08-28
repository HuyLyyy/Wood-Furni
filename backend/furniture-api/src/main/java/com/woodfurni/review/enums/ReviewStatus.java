package com.woodfurni.review.enums;

/**
 * Review visibility status.
 * - PUBLISHED: shown to customers on product page (default on create)
 * - HIDDEN: hidden by admin (policy violation, spam, etc.)
 */
public enum ReviewStatus {
    PUBLISHED,
    HIDDEN
}
