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

    // Always hit the Spring Boot context-path /api/v1/inventory/...
    // regardless of whether publicUrl is relative ("/api/inventory/..."),
    // already-prefixed ("/api/v1/inventory/..."), or absolute.
    let url;
    if (/^https?:\/\//i.test(publicUrl)) {
        // Absolute URL — swap hostname for current origin isn't needed; axios
        // will resolve relative to apiClient.baseURL anyway. Convert to a
        // path by taking the pathname + search so the Authorization header
        // and baseURL prefix logic still applies.
        try {
            const u = new URL(publicUrl);
            url = u.pathname + u.search;
        } catch {
            url = publicUrl;
        }
    } else {
        url = publicUrl;
    }

    // Normalise: any of these input forms must end up as /api/v1/inventory/...
    //   /api/inventory/evidence/...
    //   /api/v1/inventory/evidence/...
    //   /inventory/evidence/...
    //   /api/v1/evidence/...            (buggy legacy: just in case)
    if (url.includes('/evidence/')) {
        // Strip any leading /api[/v1] prefix, then rebuild as /api/v1/inventory/evidence/...
        // We split at "/evidence/" and keep the suffix to preserve path params.
        const idx = url.indexOf('/evidence/');
        const suffix = url.substring(idx); // starts with "/evidence/..."
        url = '/api/v1/inventory' + suffix;
    } else if (!url.startsWith('/api/v1/')) {
        url = '/api/v1' + (url.startsWith('/') ? url : '/' + url);
    }

    const response = await apiClient.get(url, {
        responseType: 'blob',
        // Don't let axios sniff and downgrade the Content-Type — for binary
        // downloads we want the raw response.
        transformResponse: [(data) => data],
    });

    // Server-side errors arrive as {success:false, message:"..."} JSON,
    // not as an Excel blob. axios with responseType:'blob' still gives us
    // the raw blob, so sniff the type and surface the message.
    const blob = response.data;
    if (blob instanceof Blob && blob.type && (blob.type.startsWith('application/json') || blob.type.startsWith('text/html'))) {
        const text = await blob.text();
        let msg = 'Không thể tải minh chứng';
        try {
            // Spring Boot Whitelabel error page OR our ApiResponse JSON.
            // Try JSON first.
            const parsed = JSON.parse(text);
            if (parsed && parsed.message) msg = parsed.message;
        } catch {
            // Not JSON — fall back to generic + status.
            const status = response.status;
            if (status === 404) msg = 'File minh chứng không còn tồn tại trên server (có thể đã bị xóa sau khi redeploy). Vui lòng upload lại file điều chỉnh.';
            else msg = `Lỗi tải minh chứng (HTTP ${status})`;
        }
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