package com.woodfurni.auth.enums;

/**
 * User role enumeration for WOODFURNI platform.
 *
 * - CUSTOMER: Regular end-user / buyer
 * - SALES: Sales department staff
 * - WAREHOUSE: Warehouse / inventory staff
 * - CONTENT: Content / CMS management staff
 * - ADMIN: Full system administrator
 */
public enum Role {
    CUSTOMER,
    SALES,
    WAREHOUSE,
    CONTENT,
    ADMIN,
    /** Tài xế giao hàng — dùng cho module Chuyến xe (delivery). */
    DRIVER,
    /** Nhân viên lắp ráp — dùng cho module Chuyến xe (delivery). */
    ASSEMBLER
}
