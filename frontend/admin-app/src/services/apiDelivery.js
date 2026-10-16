import apiClient from './apiClient';

/** Unwrap helper — matches pattern from other api* files in this project. */
const unwrap = (r) => r.data.data;

/**
 * Delivery / Chuyến xe API.
 *
 * Endpoints:
 *   GET  /delivery/vehicle-types               → VehicleType[]
 *   GET  /delivery/eligible-orders            → PageResponse<EligibleOrderResponse>
 *   POST /delivery/trips/preview               → TripCapacityResponse
 *   POST /delivery/trips                      → DeliveryTripResponse
 *   GET  /delivery/trips                     → PageResponse<DeliveryTripResponse>
 *   GET  /delivery/trips/{id}                → DeliveryTripResponse (with orders)
 *
 * Also:
 *   GET  /users/drivers                        → DriverResponse[]
 */
export const deliveryApi = {
    listVehicleTypes: () =>
        apiClient.get('/delivery/vehicle-types').then(unwrap),

    listEligibleOrders: (params = {}) =>
        apiClient.get('/delivery/eligible-orders', { params }).then(unwrap),

    previewCapacity: (body) =>
        apiClient.post('/delivery/trips/preview', body).then(unwrap),

    createTrip: (body) =>
        apiClient.post('/delivery/trips', body).then(unwrap),

    listTrips: (params = {}) =>
        apiClient.get('/delivery/trips', { params }).then(unwrap),

    getTripDetail: (id) =>
        apiClient.get(`/delivery/trips/${id}`).then(unwrap),

    // Trip actions
    startShipping: (id) =>
        apiClient.post(`/delivery/trips/${id}/start`).then(unwrap),

    cancelTrip: (id, reason) =>
        apiClient.post(`/delivery/trips/${id}/cancel`, { reason: reason || '' }).then(unwrap),

    completeTrip: (id) =>
        apiClient.post(`/delivery/trips/${id}/complete`).then(unwrap),

    // ─── Luồng hoãn giao hàng (bước 1 → 9) ─────────────────────────────

    /** Bước 1: Tài xế đã đến địa điểm giao. SHIPPING → ARRIVED. */
    arriveAtLocation: (id) =>
        apiClient.post(`/delivery/trips/${id}/arrive`).then(unwrap),

    /** Bước 2A: Giao thành công 1 đơn. Order → DELIVERED. */
    markOrderDelivered: (id, orderId, body) =>
        apiClient.post(`/delivery/trips/${id}/orders/${orderId}/deliver`, body).then(unwrap),

    /** Bước 2C: Không liên lạc được. Order vẫn SHIPPING, chờ tài xế xác nhận. */
    markOrderUnreachable: (id, orderId, body = {}) =>
        apiClient.post(`/delivery/trips/${id}/orders/${orderId}/unreachable`, body).then(unwrap),

    /** Bước 2B→9: Hoãn giao 1 đơn. Order → RESCHEDULED. Cảnh báo nếu lần 3. */
    rescheduleOrder: (id, orderId, body) =>
        apiClient.post(`/delivery/trips/${id}/orders/${orderId}/reschedule`, body).then(unwrap),

    /** Bước 4': Giao thất bại 1 đơn. Order → RETURNED. */
    markOrderFailed: (id, orderId, body) =>
        apiClient.post(`/delivery/trips/${id}/orders/${orderId}/fail`, body).then(unwrap),

    // Bulk actions
    bulkStartShipping: (tripIds) =>
        apiClient.post('/delivery/trips/bulk-start', { tripIds }).then(unwrap),

    bulkCancelTrips: (tripIds, reason) =>
        apiClient.post('/delivery/trips/bulk-cancel', { tripIds, reason: reason || '' }).then(unwrap),

    // ─── Reschedule flow (tạo trip giao lại) ─────────────────────────────
    /** Danh sách đơn đang RESCHEDULED — chờ tạo trip giao lại. */
    listRescheduleOrders: () =>
        apiClient.get('/delivery/reschedule-orders').then(unwrap),

    /** Tạo trip mới giao lại từ danh sách order RESCHEDULED. */
    createRescheduleTrip: (body) =>
        apiClient.post('/delivery/trips/reschedule', body).then(unwrap),
};

export const userApi = {
    listDrivers: () =>
        apiClient.get('/users/drivers').then(unwrap),
};
