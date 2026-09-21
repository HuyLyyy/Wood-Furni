import apiClient from './apiClient';

/**
 * Admin-app Print-Slips API.
 *
 *   GET  /inventory/print-templates                       → PrintSlipTypeInfo[]
 *   GET  /inventory/print-templates/{type}/download       → binary Excel blob
 *
 * Both endpoints require ADMIN or WAREHOUSE role.
 *
 * The download pattern is identical to evidence files (see
 * downloadEvidenceFile in apiAdminInventory.js): we fetch via apiClient so
 * the Bearer token is attached, then surface the blob as an in-browser
 * download trigger.
 */

/**
 * @typedef {Object} PrintSlipTypeInfo
 * @property {string} code        e.g. "NHAP_KHO" (matches PrintSlipType enum)
 * @property {string} displayName e.g. "Phiếu nhập kho"
 */

/**
 * List the available print-slip template types.
 * @returns {Promise<PrintSlipTypeInfo[]>}
 */
export async function listPrintTemplates() {
    const res = await apiClient.get('/inventory/print-templates');
    return res.data.data;
}

/**
 * Download a blank Excel template for the given slip type. The browser
 * will save the file with the server-provided filename.
 *
 * @param {string} typeCode  One of the codes from listPrintTemplates() —
 *                           "NHAP_KHO", "XUAT_KHO", "HU_HONG", "MAT_MAT",
 *                           "TRA_LAI", "CHENH_LECH_KIEM_KE", "THANH_LY".
 * @returns {Promise<void>}
 */
export async function downloadPrintTemplate(typeCode) {
    if (!typeCode) throw new Error('Loại phiếu không hợp lệ');

    // Server-rendered path = "/inventory/print-templates/{type}/download".
    // Combined with apiClient.baseURL = "/api/v1" → final URL is correct.
    const url = `/inventory/print-templates/${encodeURIComponent(typeCode)}/download`;

    const response = await apiClient.get(url, {
        responseType: 'blob',
        transformResponse: [(data) => data],
    });

    const blob = response.data;
    if (blob instanceof Blob && blob.type && blob.type.startsWith('application/json')) {
        // Backend returned a JSON error envelope — extract message.
        const text = await blob.text();
        let msg = 'Không thể tải phiếu in';
        try {
            const parsed = JSON.parse(text);
            if (parsed?.message) msg = parsed.message;
        } catch {
            // Not JSON — leave default.
        }
        throw new Error(msg);
    }

    // Read filename from Content-Disposition header, fallback to a sensible
    // default. axios gives us raw headers (may have different casing).
    const headers = response.headers || {};
    const cd = headers['content-disposition'] || headers['Content-Disposition'] || '';
    let filename = `phieu-in-${typeCode.toLowerCase()}.xlsx`;
    const utf8Match = cd.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match) {
        try {
            filename = decodeURIComponent(utf8Match[1]);
        } catch {
            /* keep fallback */
        }
    } else {
        const asciiMatch = cd.match(/filename="?([^";]+)"?/i);
        if (asciiMatch) filename = asciiMatch[1];
    }

    // Build object URL, trigger anchor click, revoke URL.
    const blobUrl = window.URL.createObjectURL(blob);
    try {
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    } finally {
        window.URL.revokeObjectURL(blobUrl);
    }
}
