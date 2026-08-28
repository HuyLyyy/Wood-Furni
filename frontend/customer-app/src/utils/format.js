export const formatCurrency = (amount, currency = 'VND') => {
    if (amount == null) return '—';
    const number = Number(amount);
    if (Number.isNaN(number)) return '—';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency,
        maximumFractionDigits: 0,
    }).format(number);
};

export const formatDate = (iso) => {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleDateString('vi-VN', {
            day: '2-digit', month: '2-digit', year: 'numeric',
        });
    } catch {
        return '—';
    }
};