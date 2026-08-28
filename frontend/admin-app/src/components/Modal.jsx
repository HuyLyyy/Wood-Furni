import PropTypes from 'prop-types';
import { useEffect } from 'react';
import './Modal.css';

/**
 * Modal — generic panel with backdrop + ESC-to-close.
 *
 *   <Modal title="..." onClose={() => ...} width={460}>
 *       ...content...
 *   </Modal>
 *
 * Body scroll is locked while open. ESC and backdrop click both close.
 */
export default function Modal({ title, onClose, children, width = 480 }) {
    useEffect(() => {
        const onKey = (e) => { if (e.key === 'Escape') onClose?.(); };
        document.addEventListener('keydown', onKey);
        const prevOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', onKey);
            document.body.style.overflow = prevOverflow;
        };
    }, [onClose]);

    return (
        <div className="modal" role="dialog" aria-modal="true">
            <div className="modal__backdrop" onClick={onClose} />
            <div className="modal__panel" style={{ width }}>
                <header className="modal__header">
                    <h3 className="modal__title">{title}</h3>
                    <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng">✕</button>
                </header>
                <div className="modal__body">{children}</div>
            </div>
        </div>
    );
}

Modal.propTypes = {
    title: PropTypes.string,
    onClose: PropTypes.func,
    children: PropTypes.node,
    width: PropTypes.number,
};