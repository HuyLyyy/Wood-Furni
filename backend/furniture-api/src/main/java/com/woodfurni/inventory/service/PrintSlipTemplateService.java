package com.woodfurni.inventory.service;

import com.woodfurni.inventory.enums.PrintSlipType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Sinh file Excel template cho các loại phiếu in kho.
 *
 * Mỗi file bao gồm:
 *   - Hàng tiêu đề: tên công ty, loại phiếu, ngày in, hướng dẫn
 *   - Hàng header bảng: STT | Mã sản phẩm | Tên sản phẩm | Số lượng | Giá thành | Ghi chú
 *   - 20 dòng trống mẫu để user biết format khi in ra giấy
 *
 * File được sinh on-demand mỗi lần user bấm "Tải về" — KHÔNG lưu GridFS
 * (template là file tĩnh, sinh lại rẻ hơn nhiều so với việc quản lý file
 * trên storage và dọn dẹp khi cập nhật template).
 */
@Service
@Slf4j
public class PrintSlipTemplateService {

    private static final int BLANK_ROWS = 20;
    private static final String COMPANY_NAME = "WOOD-FURNI";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Sinh file Excel template cho loại phiếu cho trước.
     *
     * @param type loại phiếu (xem enum PrintSlipType)
     * @return bytes của file .xlsx
     */
    public byte[] generateTemplate(PrintSlipType type) {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(type.name());

            // Column widths: STT|Mã|Tên|SL|Giá|Note
            sheet.setColumnWidth(0, 6 * 256);   // STT
            sheet.setColumnWidth(1, 15 * 256);  // Mã SP
            sheet.setColumnWidth(2, 35 * 256);  // Tên SP
            sheet.setColumnWidth(3, 12 * 256);  // Số lượng
            sheet.setColumnWidth(4, 15 * 256);  // Giá thành
            sheet.setColumnWidth(5, 30 * 256);  // Ghi chú

            // ---- Row 0: company name (merged across 6 cols) ----
            Row companyRow = sheet.createRow(0);
            companyRow.setHeightInPoints(24);
            Cell companyCell = companyRow.createCell(0);
            companyCell.setCellValue(COMPANY_NAME);
            companyCell.setCellStyle(titleStyle(workbook, 16));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            // ---- Row 1: slip type (merged) ----
            Row typeRow = sheet.createRow(1);
            typeRow.setHeightInPoints(20);
            Cell typeCell = typeRow.createCell(0);
            typeCell.setCellValue(type.getDisplayName().toUpperCase());
            typeCell.setCellStyle(titleStyle(workbook, 14));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

            // ---- Row 2: print date (merged) ----
            Row dateRow = sheet.createRow(2);
            Cell dateCell = dateRow.createCell(0);
            dateCell.setCellValue("Ngày in: " + LocalDate.now().format(DATE_FORMAT));
            dateCell.setCellStyle(italicStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 5));

            // ---- Row 3: blank spacer ----
            sheet.createRow(3);

            // ---- Row 4: header row ----
            String[] headers = {"STT", "Mã sản phẩm", "Tên sản phẩm", "Số lượng", "Giá thành (VNĐ)", "Ghi chú"};
            Row headerRow = sheet.createRow(4);
            headerRow.setHeightInPoints(22);
            CellStyle headerStyle = headerStyle(workbook);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            // ---- Rows 5..5+BLANK_ROWS-1: empty data rows with borders ----
            CellStyle dataStyle = dataStyle(workbook);
            CellStyle numberStyle = numberStyle(workbook);
            CellStyle centerStyle = centerStyle(workbook);

            for (int r = 0; r < BLANK_ROWS; r++) {
                Row row = sheet.createRow(5 + r);
                row.setHeightInPoints(18);

                Cell sttCell = row.createCell(0);
                sttCell.setCellValue(r + 1);
                sttCell.setCellStyle(centerStyle);

                for (int c = 1; c <= 5; c++) {
                    Cell cell = row.createCell(c);
                    if (c == 3 || c == 4) {
                        cell.setCellStyle(numberStyle);
                    } else {
                        cell.setCellStyle(dataStyle);
                    }
                }
            }

            // ---- Footer rows: signature lines ----
            int footerStart = 5 + BLANK_ROWS + 1;
            Row footerLabelRow = sheet.createRow(footerStart);
            footerLabelRow.setHeightInPoints(20);

            String[] footerLabels = {"Người lập phiếu", "Thủ kho", "Kế toán"};
            // 3 columns, each spans 2 cells
            for (int i = 0; i < 3; i++) {
                int firstCol = i * 2;
                Cell labelCell = footerLabelRow.createCell(firstCol);
                labelCell.setCellValue(footerLabels[i]);
                labelCell.setCellStyle(boldStyle(workbook));
                sheet.addMergedRegion(new CellRangeAddress(footerStart, footerStart, firstCol, firstCol + 1));
            }

            // ---- Print directives (Vietnamese A4 portrait) ----
            sheet.getPrintSetup().setLandscape(false);
            sheet.getPrintSetup().setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
            sheet.setFitToPage(true);
            sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.LeftMargin, 0.5);
            sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.RightMargin, 0.5);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Wrap so the controller layer can map to 500.
            throw new RuntimeException("Failed to generate Excel template for " + type, e);
        }
    }

    // ============== Cell styles ==============

    private CellStyle titleStyle(Workbook wb, int fontSize) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) fontSize);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle italicStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(style);
        return style;
    }

    private CellStyle dataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        applyBorders(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle numberStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        applyBorders(style);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private CellStyle centerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        applyBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
