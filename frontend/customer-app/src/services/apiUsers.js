import apiClient from './apiClient';

const unwrap = (r) => r.data.data;

export const usersApi = {
    listAddresses: () => apiClient.get('/users/me/addresses').then(unwrap),

    addAddress: (address) => apiClient.post('/users/me/addresses', address).then(unwrap),

    updateAddress: (addressId, address) =>
        apiClient.put(`/users/me/addresses/${addressId}`, address).then(unwrap),

    deleteAddress: (addressId) =>
        apiClient.delete(`/users/me/addresses/${addressId}`).then(unwrap),
};
