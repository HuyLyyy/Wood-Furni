import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';
import './CancelOrderModal.css';

/**
 * CancelOrderModal — modal that lets the customer pick a reason for
 * cancelling an order (status: PENDING / CONFIRMED).
 *
 * Renders a fixed list of common reasons plus an "Lý do khác" option
 * that reveals a free-text textarea. The form cannot be submitted
 * until the user picks one of the predefined reasons OR fills in a
 * custom reason of at least 5 characters.
 *
 * Returns the selected reason via `onConfirm(reason)` (never null/empty)
 * or `null` via `onCancel()`.
 */

export const CANCEL_REASONS = [
    { value: 'CHANGED_MIND', label: 'Tôi đổi ý, không muốn mua nữa' },
    { value: 'FOUND_CHEAPER', label: 'Tìm được nơi bán rẻ hơn' },
    { value: 'WRONG_INFO', label: 'Đặt nhầm sản phẩm / sai thông tin' },
    { value: 'DELIVERY_TOO_LONG', label: 'Thời gian giao hàng quá lâu' },
    { value: 'PAYMENT_ISSUE', label: 'Gặp vấn đề khi thanh toán' },
];

const OTHER_VALUE = 'OTHER';

export default function CancelOrderModal({
    open,
    orderNumber,
    loading = false,
    onConfirm,
    onCancel,
}) {
    const [selected, setSelected] = useState('');
    const [otherText, setOtherText] = useState('');

    // Reset state every time the modal is reopened.
    useEffect(() => {
        if (open) {
            setSelected('');
            setOtherText('');
        }
    }, [open]);

    if (!open) return null;

    const isOther = selected === OTHER_VALUE;
    const otherValid = !isOther || otherText.trim().length >= 5;
    const canSubmit = selected !== '' && otherValid;

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit || loading) return;

        const predefined = CANCEL_REASONS.find((r) => r.value === selected);
        const reason = isOther
            ? otherText.trim()
            : predefined
                ? `[${predefined.value}] ${predefined.label}`
                : otherText.trim();
        onConfirm(reason);
    };

    return (
        <div className="cancel-modal" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
            <div className="cancel-modal__backdrop" onClick={loading ? undefined : onCancel} />
            <form className="cancel-modal__panel" onSubmit={handleSubmit}>
                <h3 id="cancel-modal-title" className="cancel-modal__title">
                    Huỷ đơn hàng
                </h3>
                <p className="cancel-modal__subtitle">
                    Vui lòng cho WOODFURNI biết lý do bạn muốn huỷ đơn
                    {orderNumber ? (
                        <>
                            {' '}
                            <strong>{orderNumber}</strong>
                        </>
                    ) : null}
                    . Hành động này không thể hoàn tác.
                </p>

                <div className="cancel-modal__reasons" role="radiogroup" aria-label="Lý do huỷ đơn">
                    {CANCEL_REASONS.map((reason) => (
                        <label
                            key={reason.value}
                            className={`cancel-modal__reason ${
                                selected === reason.value ? 'is-selected' : ''
                            }`}
                        >
                            <input
                                type="radio"
                                name="cancel-reason"
                                value={reason.value}
                                checked={selected === reason.value}
                                onChange={() => setSelected(reason.value)}
                                disabled={loading}
                            />
                            <span className="cancel-modal__reason-radio" aria-hidden="true" />
                            <span className="cancel-modal__reason-label">{reason.label}</span>
                        </label>
                    ))}

                    <label
                        className={`cancel-modal__reason ${
                            isOther ? 'is-selected' : ''
                        }`}
                    >
                        <input
                            type="radio"
                            name="cancel-reason"
                            value={OTHER_VALUE}
                            checked={isOther}
                            onChange={() => setSelected(OTHER_VALUE)}
                            disabled={loading}
                        />
                        <span className="cancel-modal__reason-radio" aria-hidden="true" />
                        <span className="cancel-modal__reason-label">Lý do khác</span>
                    </label>
                </div>

                {isOther && (
                    <div className="cancel-modal__other">
                        <label htmlFor="cancel-other-text" className="cancel-modal__other-label">
                            Vui lòng mô tả lý do của bạn (tối thiểu 5 ký tự)
                        </label>
                        <textarea
                            id="cancel-other-text"
                            className="cancel-modal__textarea"
                            rows={3}
                            maxLength={500}
                            value={otherText}
                            onChange={(e) => setOtherText(e.target.value)}
                            disabled={loading}
                            placeholder="Nhập lý do huỷ đơn..."
                        />
                        <div className="cancel-modal__counter">
                            {otherText.trim().length}/500
                        </div>
                    </div>
                )}

                <div className="cancel-modal__actions">
                    <button
                        type="button"
                        className="cancel-modal__btn cancel-modal__btn--ghost"
                        onClick={onCancel}
                        disabled={loading}
                    >
                        Đóng
                    </button>
                    <button
                        type="submit"
                        className="cancel-modal__btn cancel-modal__btn--danger"
                        disabled={!canSubmit || loading}
                    >
                        {loading ? 'Đang huỷ...' : 'Xác nhận huỷ đơn'}
                    </button>
                </div>
            </form>
        </div>
    );
}

CancelOrderModal.propTypes = {
    open: PropTypes.bool,
    orderNumber: PropTypes.string,
    loading: PropTypes.bool,
    onConfirm: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
};
