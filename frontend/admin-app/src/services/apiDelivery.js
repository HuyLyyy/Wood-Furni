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
};

export const userApi = {
    listDrivers: () =>
        apiClient.get('/users/drivers').then(unwrap),
};
