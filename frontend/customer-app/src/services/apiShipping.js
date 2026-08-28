import apiClient from './apiClient';

const unwrap = (r) => r.data.data;

export const shippingApi = {
    /**
     * Preview shipping fee for a delivery address.
     * POST /shipping/calculate
     *
     * @param {string} city
     * @param {string} district
     * @returns {{ fee, distanceKm, isOutOfProvince }}
     */
    calculate: (city, district) =>
        apiClient.post('/shipping/calculate', { city, district }).then(unwrap),

    /**
     * Fetch the full HCM district + ward tree.
     * GET /shipping/districts
     *
     * @returns {{ city, districts: [{ name, wards: string[] }] }}
     */
    getDistricts: () =>
        apiClient.get('/shipping/districts').then(unwrap),
};
