package com.woodfurni.inventory.enums;

/**
 * Các loại phiếu in (template) dùng trong nghiệp vụ kho.
 *
 * Mỗi phiếu được sinh dưới dạng file Excel (.xlsx) trống để admin /
 * warehouse staff tải về, in ra, điền thông tin bằng tay (mã SP, tên SP,
 * số lượng, giá thành, ghi chú), rồi upload lại làm file minh chứng
 * trong luồng điều chỉnh tồn kho.
 *
 * Mapping từ reasonCode trong InventoryHistory.reasonCode:
 *   MUA_TAI_CUA_HANG    ←  MUA_HANG_TAI_CUA_HANG
 *   NHAP_KHO       ←  IMPORT  (legacy)  và  NHAP_KHO_HANG_BAN (đơn nhập mới)
 *   XUAT_KHO       ←  EXPORT
 *   HU_HONG        ←  DAMAGE
 *   MAT_MAT        ←  LOSS
 *   TRA_LAI        ←  CUSTOMER_RETURN
 *   CHENH_LECH_KIEM_KE ← STOCKTAKE_VARIANCE
 *   THANH_LY       ←  LIQUIDATION
 */
public enum PrintSlipType {
    MUA_TAI_CUA_HANG("Phiếu bán hàng tại cửa hàng", "Mau-phieu-mua-tai-cua-hang"),
    NHAP_KHO("Phiếu nhập kho", "Mau-phieu-nhap-kho"),
    XUAT_KHO("Phiếu xuất kho", "Mau-phieu-xuat-kho"),
    HU_HONG("Phiếu hàng hư hỏng", "Mau-phieu-hu-hong"),
    MAT_MAT("Phiếu hàng mất mát", "Mau-phieu-mat-mat"),
    TRA_LAI("Phiếu hàng trả lại từ khách", "Mau-phieu-tra-lai"),
    CHENH_LECH_KIEM_KE("Phiếu chênh lệch qua kiểm kê", "Mau-phieu-chenh-lech-kiem-ke"),
    THANH_LY("Phiếu thanh lý hàng tồn kho", "Mau-phieu-thanh-ly");

    private final String displayName;
    private final String fileNamePrefix;

    PrintSlipType(String displayName, String fileNamePrefix) {
        this.displayName = displayName;
        this.fileNamePrefix = fileNamePrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileNamePrefix() {
        return fileNamePrefix;
    }
}
