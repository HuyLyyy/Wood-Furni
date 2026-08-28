const ADDRESSES_KEY = 'woodfurni_saved_addresses';

/**
 * Local address storage. Used because the backend currently does not
 * expose a GET /users/me with addresses endpoint.
 *
 * When the backend ships one, swap this module to read from that endpoint
 * — the call sites don't need to change.
 */
export const addressStorage = {
    list() {
        try {
            const raw = localStorage.getItem(ADDRESSES_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch {
            return [];
        }
    },
    save(address) {
        const list = this.list();
        const id = address.id || `addr_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
        const newAddress = { ...address, id };
        // If marked as default, clear default on all others
        if (newAddress.isDefault) {
            list.forEach((a) => { a.isDefault = false; });
        }
        const existingIndex = list.findIndex((a) => a.id === id);
        if (existingIndex >= 0) {
            list[existingIndex] = newAddress;
        } else {
            list.push(newAddress);
        }
        localStorage.setItem(ADDRESSES_KEY, JSON.stringify(list));
        return newAddress;
    },
    remove(id) {
        const list = this.list().filter((a) => a.id !== id);
        localStorage.setItem(ADDRESSES_KEY, JSON.stringify(list));
    },
    getDefault() {
        const list = this.list();
        return list.find((a) => a.isDefault) || list[0] || null;
    },
};