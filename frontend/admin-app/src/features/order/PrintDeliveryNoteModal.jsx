import { createPortal } from 'react-dom';
import DeliveryNotePrint from './DeliveryNotePrint.jsx';
import './PrintDeliveryNoteModal.css';

/**
 * PrintDeliveryNoteModal
 *
 * Renders a full-screen overlay showing a preview of the delivery note.
 * The note itself is always mounted in the DOM but hidden off-screen;
 * when the user clicks "In phiếu" we call window.print() and the
 * @media print CSS rule switches the note from display:none → display:block
 * and hides the app shell — so the browser only prints the note itself.
 *
 * Usage:
 *   <PrintDeliveryNoteModal order={order} onClose={onClose} />
 */
export default function PrintDeliveryNoteModal({ order, onClose }) {
    const handlePrint = () => {
        // Brief delay so the button's active state clears before print
        setTimeout(() => window.print(), 50);
    };

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) onClose();
    };

    const overlay = (
        <div className="pdn-overlay" onClick={handleBackdropClick}>
            {/* Toolbar — visible on screen, hidden when printing */}
            <div className="pdn-toolbar">
                <div className="pdn-toolbar__left">
                    <span className="pdn-toolbar__title">
                        📄 Phiếu giao hàng — {order?.orderNumber || order?.id}
                    </span>
                </div>
                <div className="pdn-toolbar__actions">
                    <button className="pdn-btn pdn-btn--ghost" onClick={onClose}>
                        Đóng
                    </button>
                    <button className="pdn-btn pdn-btn--primary" onClick={handlePrint}>
                        🖨️ In phiếu giao hàng
                    </button>
                </div>
            </div>

            {/* Delivery note preview — visible on screen; @media print hides
                everything else and makes this fill the page. */}
            <div className="pdn-note">
                <DeliveryNotePrint order={order} />
            </div>
        </div>
    );

    return createPortal(overlay, document.body);
}
