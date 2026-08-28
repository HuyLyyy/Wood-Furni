import apiClient from './apiClient';

/**
 * Storage API — wraps the backend StorageController.
 *
 *   POST /storage/upload   multipart/form-data  → ApiResponse<string[]>
 *     (array of relative URLs like /uploads/abc123.jpg)
 */
export const storageApi = {
    /**
     * Upload one or more image files.
     * @param {File[]} files  — array of File objects from <input type="file">
     * @returns {Promise<string[]>}  array of relative URLs
     */
    uploadImages: (files) => {
        const form = new FormData();
        files.forEach((f) => form.append('files', f));
        // NOTE: do NOT set Content-Type header manually — axios must set it
        // with the correct multipart boundary so Spring can parse the request.
        return apiClient
            .post('/storage/upload', form, {
                headers: { 'Content-Type': 'multipart/form-data' },
            })
            .then((r) => r.data.data); // unwrap ApiResponse<string[]>
    },
};
