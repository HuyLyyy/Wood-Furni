import PropTypes from 'prop-types';
import { useState } from 'react';
import './ConfirmDialog.css';

/**
 * ConfirmDialog — modal with title + message + 2 buttons.
 * Returns an object compatible with the imperative `useConfirmDialog` hook.
 *
 * Two usage modes:
 *   1. State-driven via prop (preferred for one-off confirms in components):
 *
 *        <ConfirmDialog
 *          open={show}
 *          title="..."
 *          message="..."
 *          onConfirm={...}
 *          onCancel={...}
 *        />
 *
 *   2. Hook-driven (preferred for "fire and forget" confirmations):
 *
 *        const confirm = useConfirmDialog();
 *        confirm({ title, message, confirmLabel: 'Xoá' }).then(ok => { ... });
 */
export default function ConfirmDialog({
    open,
    title,
    message,
    confirmLabel = 'Xác nhận',
    cancelLabel = 'Huỷ',
    danger = false,
    loading = false,
    onConfirm,
    onCancel,
}) {
    if (!open) return null;

    return (
        <div className="confirm-dialog" role="dialog" aria-modal="true">
            <div className="confirm-dialog__backdrop" onClick={onCancel} />
            <div className="confirm-dialog__panel">
                {title && <h3 className="confirm-dialog__title">{title}</h3>}
                {message && <p className="confirm-dialog__message">{message}</p>}
                <div className="confirm-dialog__actions">
                    <button
                        type="button"
                        className="confirm-dialog__btn confirm-dialog__btn--ghost"
                        onClick={onCancel}
                        disabled={loading}
                    >
                        {cancelLabel}
                    </button>
                    <button
                        type="button"
                        className={`confirm-dialog__btn ${danger ? 'confirm-dialog__btn--danger' : 'confirm-dialog__btn--primary'}`}
                        onClick={onConfirm}
                        disabled={loading}
                    >
                        {loading ? 'Đang xử lý...' : confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}

ConfirmDialog.propTypes = {
    open: PropTypes.bool,
    title: PropTypes.string,
    message: PropTypes.string,
    confirmLabel: PropTypes.string,
    cancelLabel: PropTypes.string,
    danger: PropTypes.bool,
    loading: PropTypes.bool,
    onConfirm: PropTypes.func,
    onCancel: PropTypes.func,
};

/**
 * Imperative handle. Returns a `confirm(opts)` function that resolves true/false
 * based on the user's choice.
 *
 * Usage:
 *   const confirm = useConfirmDialog();
 *   const ok = await confirm({ title: '...', message: '...' });
 *   if (ok) { ... }
 */
export function useConfirmDialog() {
    const [state, setState] = useState({
        open: false,
        opts: null,
        resolver: null,
    });

    const confirm = (opts) => {
        return new Promise((resolve) => {
            setState({ open: true, opts, resolver: resolve });
        });
    };

    const handleConfirm = () => {
        state.resolver?.(true);
        setState({ open: false, opts: null, resolver: null });
    };

    const handleCancel = () => {
        state.resolver?.(false);
        setState({ open: false, opts: null, resolver: null });
    };

    const dialog = state.opts ? (
        <ConfirmDialog
            open={state.open}
            title={state.opts.title}
            message={state.opts.message}
            confirmLabel={state.opts.confirmLabel}
            cancelLabel={state.opts.cancelLabel}
            danger={state.opts.danger}
            loading={state.opts.loading}
            onConfirm={handleConfirm}
            onCancel={handleCancel}
        />
    ) : null;

    return { confirm, dialog };
}