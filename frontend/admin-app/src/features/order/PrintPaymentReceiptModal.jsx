import { createPortal } from 'react-dom';
import PaymentReceiptPrint from './PaymentReceiptPrint.jsx';
import './PrintDeliveryNoteModal.css';

/**
 * PrintPaymentReceiptModal
 *
 * Full-screen overlay that previews the Vietnamese-style payment receipt
 * (biên nhận thu tiền) and lets the user print it via the browser print
 * dialog.  Reuses the same print-CSS strategy as PrintDeliveryNoteModal:
 * the receipt is always mounted, the @media print rule hides everything
 * except the receipt body.
 *
 * Usage:
 *   <PrintPaymentReceiptModal order={order} onClose={onClose} />
 */
export default function PrintPaymentReceiptModal({ order, onClose }) {
    const handlePrint = () => {
        setTimeout(() => window.print(), 50);
    };

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) onClose();
    };

    const overlay = (
        <div className="pdn-overlay" onClick={handleBackdropClick}>
            <div className="pdn-toolbar">
                <div className="pdn-toolbar__left">
                    <span className="pdn-toolbar__title">
                        💵 Biên nhận thu tiền — {order?.orderNumber || order?.id}
                    </span>
                </div>
                <div className="pdn-toolbar__actions">
                    <button className="pdn-btn pdn-btn--ghost" onClick={onClose}>
                        Đóng
                    </button>
                    <button className="pdn-btn pdn-btn--primary" onClick={handlePrint}>
                        🖨️ In biên nhận
                    </button>
                </div>
            </div>

            <div className="pdn-note">
                <PaymentReceiptPrint order={order} />
            </div>
        </div>
    );

    return createPortal(overlay, document.body);
}
