package com.woodfurni.inventory.enums;

/**
 * Fixed, controlled vocabulary of reasons why a stock adjustment is made.
 *
 * Frontend renders these as a checklist — staff picks exactly one. The
 * backend rejects any value outside this list so audit trails stay
 * consistent and reportable.
 */
public enum AdjustmentReason {
    /** Bán hàng trực tiếp tại cửa hàng (ghi nhận đơn bán lẻ, trừ tồn kho). */
    MUA_HANG_TAI_CUA_HANG,
    /** Nhập kho hàng bán — tăng tồn kho từ đơn nhập mới (delta > 0). */
    NHAP_KHO_HANG_BAN,
    /** Hư hỏng trong kho (mối mọt, ẩm mốc, vỡ… không do khách). */
    DAMAGE_STOCK,
    /** Mất mát / thất thoát trong kho (trộm, thất lạc khi di chuyển). */
    LOSS_THEFT,
    /** Hàng khách trả lại — nhập kho lại hoặc xuất hẳn ra ngoài. */
    CUSTOMER_RETURN,
    /** Kiểm kê định kỳ phát hiện chênh lệch so với sổ sách. */
    STOCKTAKE_VARIANCE,
    /** Thanh lý hàng tồn kho (hết mẫu, hết vòng đời sản phẩm). */
    LIQUIDATION;

    public static AdjustmentReason fromCode(String code) {
        if (code == null) return null;
        for (AdjustmentReason r : values()) {
            if (r.name().equalsIgnoreCase(code.trim())) return r;
        }
        return null;
    }
}
