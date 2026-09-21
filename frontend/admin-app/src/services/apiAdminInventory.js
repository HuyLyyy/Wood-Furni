import apiClient, { tokenStorage } from './apiClient';

/**
 * Admin-app inventory API.
 *
 *   GET  /inventory?page=&size=                           → PageResponse<InventoryResponse>
 *   GET  /inventory/low-stock?page=&size=                  → PageResponse<InventoryResponse>
 *   GET  /inventory/{productId}                            → InventoryResponse
 *   PATCH /inventory/{productId}/adjust                     body: FormData { reasonCode, delta, note?, evidence }
 *   GET  /inventory/{productId}/history?page=&size=        → PageResponse<InventoryHistoryResponse>
 *   GET  /inventory/evidence/{yyyy-MM}/{filename}          → binary Excel blob
 *
 * The adjust endpoint requires multipart/form-data so that a binary Excel
 * file can be uploaded alongside the structured fields.
 *
 * Backend pre-authorizes WAREHOUSE/ADMIN at controller level.
 */
const unwrap = (r) => r.data.data;

/**
 * Download an evidence file from a public URL like
 * "/api/inventory/evidence/2026-09/abcd.xlsx".
 *
 * We can't just use <a href=...> because:
 *   - The endpoint is behind Spring Security (Bearer token required).
 *   - Opening the URL in a new tab hits nginx SPA fallback which serves
 *     index.html as the response body, causing Excel viewer to render an
 *     empty file with a generic name.
 *
 * Instead we fetch the URL via apiClient (which attaches Authorization),
 * convert to Blob, then trigger an in-memory <a download> click.
 *
 * @param {string} publicUrl  Relative URL from evidence.evidenceUrl
 * @param {string} originalName  Desired download filename (from evidence.evidenceOriginalName)
 * @returns {Promise<void>}
 */
export async function downloadEvidenceFile(publicUrl, originalName) {
    if (!publicUrl) throw new Error('URL minh chứng không hợp lệ');

    // Ensure we hit the Spring Boot gateway, not the SPA fallback.
    // publicUrl may be "/api/inventory/evidence/..." or already include "/api/v1".
    let url = publicUrl;
    if (url.startsWith('/api/inventory/')) {
        url = '/api/v1/inventory/' + url.slice('/api/inventory/'.length);
    } else if (!url.startsWith('/api/v1/') && !url.startsWith('http')) {
        url = '/api/v1' + (url.startsWith('/') ? url : '/' + url);
    }

    const response = await apiClient.get(url, { responseType: 'blob' });

    // Server-side errors arrive as {success:false, message:"..."} JSON,
    // not as an Excel blob. axios with responseType:'blob' still gives us
    // the raw blob, so sniff the type and surface the message.
    const blob = response.data;
    if (blob instanceof Blob && blob.type && blob.type.startsWith('application/json')) {
        const text = await blob.text();
        let msg = 'Không thể tải minh chứng';
        try {
            const parsed = JSON.parse(text);
            if (parsed && parsed.message) msg = parsed.message;
        } catch { /* keep generic */ }
        throw new Error(msg);
    }

    const filename = originalName || extractFilename(publicUrl) || 'minh-chung.xlsx';
    triggerBrowserDownload(blob, filename);
}

function extractFilename(url) {
    try {
        const parts = url.split('/');
        const last = parts[parts.length - 1];
        return last && last.includes('.') ? last : null;
    } catch {
        return null;
    }
}

function triggerBrowserDownload(blob, filename) {
    const blobUrl = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = filename; // tells browser to save with this name
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    // Free the blob URL on next tick to ensure the download has started.
    setTimeout(() => window.URL.revokeObjectURL(blobUrl), 1000);
}

export const adminInventoryApi = {
    list: (params) => apiClient.get('/inventory', { params }).then(unwrap),
    listLowStock: (params) => apiClient.get('/inventory/low-stock', { params }).then(unwrap),
    getByProductId: (productId) =>
        apiClient.get(`/inventory/${productId}`).then(unwrap),
    /**
     * Adjust stock with mandatory Excel evidence file.
     *
     * @param {string} productId
     * @param {{ reasonCode: string, delta: number, note?: string }} request
     * @param {File} evidence  — Excel file (.x.xlsx/.xls), max 10 MB
     */
    adjust: (productId, { reasonCode, delta, note }, evidence) => {
        const body = new FormData();
        body.append('reasonCode', reasonCode);
        body.append('delta', String(delta));
        if (note && note.trim()) body.append('note', note.trim());
        body.append('evidence', evidence); // filename + content-type inferred by browser
        return apiClient.patch(`/inventory/${productId}/adjust`, body, {
            headers: { 'Content-Type': 'multipart/form-data' },
        }).then(unwrap);
    },
    getHistory: (productId, params) =>
        apiClient.get(`/inventory/${productId}/history`, { params }).then(unwrap),
};

// Re-export tokenStorage so consumers can read the token if they need to
// build a custom download URL outside the helper above.
export { tokenStorage };