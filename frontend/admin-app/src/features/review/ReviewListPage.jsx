import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import useAdminReviews from '../../hooks/useAdminReviews.js';
import {
    DataTable, AdminPagination, FormField, useConfirmDialog,
} from '../../components/index.js';
import {
    REVIEW_STATUS, reviewStatusLabel, reviewStatusTone,
    REVIEW_RATINGS, reviewRatingLabel,
} from '../../utils/reviewMeta.js';
import { formatDateTime } from '../../utils/format.js';
import { adminReviewsApi } from '../../services/apiAdminReviews.js';
import './ReviewListPage.css';

/**
 * ReviewListPage
 *
 * Filters (URL-synced):
 *   - rating (1-5)
 *   - status (PUBLISHED|HIDDEN)
 *
 * Row actions: Ẩn / Hiện theo status hiện tại.
 */
export default function ReviewListPage() {
    usePageTitle('Đánh giá');
    const { confirm, dialog } = useConfirmDialog();

    const {
        items, totalElements, totalPages, page, loading, error,
        filters, setFilters, setPage, refresh,
    } = useAdminReviews();

    const handleToggle = async (row) => {
        const nextStatus = row.status === 'PUBLISHED' ? 'HIDDEN' : 'PUBLISHED';
        const action = nextStatus === 'HIDDEN' ? 'Ẩn' : 'Hiện';
        const ok = await confirm({
            title: `${action} đánh giá?`,
            message: `Đánh giá của "${row.userFullName || row.userId}" trên "${row.productName}" sẽ ${nextStatus === 'HIDDEN' ? 'bị ẩn khỏi' : 'hiện lại trên'} trang sản phẩm.`,
            confirmLabel: action,
            danger: nextStatus === 'HIDDEN',
        });
        if (!ok) return;
        try {
            await adminReviewsApi.updateStatus(row.id, nextStatus);
            toast.success(`Đã ${action.toLowerCase()} đánh giá`);
            refresh();
        } catch { /* toast by interceptor */ }
    };

    const columns = [
        {
            key: 'product', header: 'Sản phẩm', width: 280,
            render: (r) => <div className="review-row__product">{r.productName || '—'}</div>,
        },
        {
            key: 'user', header: 'Người đánh giá',
            render: (r) => (
                <div>
                    <div>{r.userFullName || '—'}</div>
                    <small style={{ color: '#94a3b8' }}>
                        {r.orderNumber ? `Đơn ${r.orderNumber}` : ''}
                    </small>
                </div>
            ),
        },
        {
            key: 'rating', header: 'Sao', width: 90,
            render: (r) => (
                <span className="review-row__rating" title={`${r.rating}/5`}>
                    {reviewRatingLabel(r.rating)}
                </span>
            ),
        },
        {
            key: 'comment', header: 'Bình luận',
            render: (r) => <div className="review-row__comment">{r.comment || '—'}</div>,
        },
        {
            key: 'createdAt', header: 'Ngày', width: 150,
            render: (r) => formatDateTime(r.createdAt),
        },
        {
            key: 'status', header: 'TT', width: 120,
            render: (r) => {
                const tone = reviewStatusTone(r.status);
                return <span className={`status-badge status-badge--${tone}`}>{reviewStatusLabel(r.status)}</span>;
            },
        },
        {
            key: 'actions', header: '', width: 130, align: 'right',
            render: (r) => (
                <div className="review-row__actions" onClick={(e) => e.stopPropagation()}>
                    <button
                        type="button"
                        className={r.status === 'PUBLISHED' ? 'danger' : ''}
                        onClick={() => handleToggle(r)}
                    >
                        {r.status === 'PUBLISHED' ? 'Ẩn' : 'Hiện'}
                    </button>
                </div>
            ),
        },
    ];

    return (
        <div className="admin-page review-list-page">
            <header className="admin-page__header">
                <div>
                    <h1>Đánh giá sản phẩm</h1>
                    <p className="admin-page__sub">{totalElements} review</p>
                </div>
            </header>

            <section
                className="order-list-page__filters"
                style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                    gap: 12,
                    marginBottom: 16,
                }}
            >
                <FormField label="Số sao" htmlFor="filter-rating">
                    <select
                        id="filter-rating"
                        value={filters.rating || ''}
                        onChange={(e) => setFilters({ rating: e.target.value })}
                    >
                        <option value="">Tất cả</option>
                        {REVIEW_RATINGS.map((r) => (
                            <option key={r} value={r}>{r} sao</option>
                        ))}
                    </select>
                </FormField>

                <FormField label="Trạng thái" htmlFor="filter-status">
                    <select
                        id="filter-status"
                        value={filters.status || ''}
                        onChange={(e) => setFilters({ status: e.target.value })}
                    >
                        <option value="">Tất cả</option>
                        {REVIEW_STATUS.map((s) => (
                            <option key={s.value} value={s.value}>{s.label}</option>
                        ))}
                    </select>
                </FormField>
            </section>

            {error ? (
                <div className="review-list-page__error">
                    <strong>Không thể tải danh sách đánh giá.</strong> {error?.message}
                </div>
            ) : (
                <>
                    <DataTable
                        columns={columns}
                        rows={items}
                        loading={loading}
                        rowKey="id"
                        emptyText="Không có review nào khớp bộ lọc."
                    />
                    <AdminPagination
                        page={page}
                        totalPages={totalPages}
                        onChange={setPage}
                    />
                </>
            )}

            {dialog}
        </div>
    );
}
