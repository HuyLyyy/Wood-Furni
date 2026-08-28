// Lightweight client-side validators. The backend is the source of truth
// and will re-validate; these are just for an immediate UX feedback loop.

export const validators = {
    email: (value) => {
        if (!value) return 'Email là bắt buộc';
        const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return re.test(value) ? null : 'Email không hợp lệ';
    },

    password: (value) => {
        if (!value) return 'Mật khẩu là bắt buộc';
        if (value.length < 8) return 'Mật khẩu phải có ít nhất 8 ký tự';
        return null;
    },

    fullName: (value) => {
        if (!value || !value.trim()) return 'Họ tên là bắt buộc';
        if (value.trim().length < 2) return 'Họ tên phải có ít nhất 2 ký tự';
        return null;
    },

    phone: (value) => {
        if (!value) return null; // optional
        const re = /^[0-9+\-\s()]{8,20}$/;
        return re.test(value) ? null : 'Số điện thoại không hợp lệ';
    },
};

/**
 * Run a field map against validators and return the first error per field,
 * or an empty object if all pass.
 *
 * @param {Record<string, string>} values
 * @param {Record<string, (v: string) => string|null>} rules
 */
export function validate(values, rules) {
    const errors = {};
    for (const [field, rule] of Object.entries(rules)) {
        const message = rule(values[field] ?? '');
        if (message) errors[field] = message;
    }
    return errors;
}