import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import useAdminProducts from '../../hooks/useAdminProducts.js';
import { useCategoriesFlat } from '../../hooks/useAdminCategories.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { adminCatalogApi } from '../../services/apiAdminCatalog.js';
import {
    Button, DataTable, AdminPagination, FormField, useConfirmDialog,
} from '../../components/index.js';
import { formatCurrency, formatDate } from '../../utils/format.js';
import { envLabel, roomLabel, productStatus } from '../../utils/catalogMeta.js';
import { can } from '../../utils/permissions.js';
import './ProductListPage.css';

/**
 * ProductListPage
 *
 * URL-synced filters: keyword, status, category, page, sort.
 *
 * Permissions:
 *   - create button        → CONTENT, ADMIN
 *   - edit / publish       → CONTENT, ADMIN
 *   - delete               → ADMIN only
 *
 * Backend enforces the same; UI hides forbidden buttons.
 */
export default function ProductListPage() {
    usePageTitle('Sản phẩm');
    const navigate = useNavigate();
    const { user } = useAuth();
    const { confirm, dialog } = useConfirmDialog();

    const role = user?.role;
    const canCreate = can(role, 'products:create');
    const canEdit   = can(role, 'products:update');
    const canDelete = can(role, 'products:delete');

    const {
        items, totalElements, totalPages, page, loading, error,
        filters, setFilters, setPage, refresh,
    } = useAdminProducts();

    const { data: categoriesFlat } = useCategoriesFlat();

    // -------- publish/unpublish --------
    const handlePublishToggle = async (product) => {
        const nextStatus = product.status === 'ACTIVE' ? 'DRAFT' : 'ACTIVE';
        if (nextStatus === 'ACTIVE' && (!product.images || product.images.length === 0)) {
            toast.error('Sản phẩm phải có ít nhất 1 ảnh trước khi publish');
            return;
        }
        try {
            await adminCatalogApi.changeProductStatus(product.id, nextStatus);
            toast.success(nextStatus === 'ACTIVE' ? 'Đã publish' : 'Đã chuyển về bản nháp');
            refresh();
        } catch { /* toast by interceptor */ }
    };

    // -------- delete --------
    const handleDelete = async (product) => {
        const ok = await confirm({
            title: 'Xoá sản phẩm?',
            message: `Bạn có chắc muốn xoá "${product.name}"? Hành động này không thể hoàn tác.`,
            confirmLabel: 'Xoá',
            danger: true,
        });
        if (!ok) return;
        try {
            await adminCatalogApi.deleteProduct(product.id);
            toast.success('Đã xoá sản phẩm');
            refresh();
        } catch { /* toast by interceptor */ }
    };

    const columns = [
        {
            key: 'image', header: 'Ảnh', width: 64,
            render: (p) => (
                <div className="product-row__image">
                    <img
                        src={(p.images && p.images[0]) || '/placeholder-product.svg'}
                        alt={p.name}
                        onError={(e) => { e.currentTarget.src = '/placeholder-product.svg'; }}
                    />
                </div>
            ),
        },
        {
            key: 'name', header: 'Tên sản phẩm',
            render: (p) => (
                <div>
                    <div className="product-row__name">{p.name}</div>
                    <div className="product-row__sku">SKU: {p.sku}</div>
                </div>
            ),
        },
        {
            key: 'category', header: 'Danh mục',
            render: (p) => p.categoryName || '—',
        },
        {
            key: 'env', header: 'Môi trường', width: 110,
            render: (p) => envLabel(p.environment),
        },
        {
            key: 'room', header: 'Phòng', width: 130,
            render: (p) => roomLabel(p.room) || '—',
        },
        {
            key: 'price', header: 'Giá', width: 130, align: 'right',
            render: (p) => (
                <div className="product-row__price">
                    <strong>{formatCurrency(p.salePrice || p.price)}</strong>
                    {p.salePrice != null && Number(p.salePrice) > 0 && Number(p.salePrice) < Number(p.price) && (
                        <span className="product-row__price-old">{formatCurrency(p.price)}</span>
                    )}
                </div>
            ),
        },
        {
            key: 'status', header: 'Trạng thái', width: 140,
            render: (p) => {
                const s = productStatus(p.status);
                return <span className={`status-badge status-badge--${s.color}`}>{s.label}</span>;
            },
        },
        {
            key: 'createdAt', header: 'Ngày tạo', width: 120,
            render: (p) => formatDate(p.createdAt),
        },
        {
            key: 'actions', header: '', width: 220, align: 'right',
            render: (p) => (
                <div className="product-row__actions" onClick={(e) => e.stopPropagation()}>
                    {canEdit && (
                        <>
                            <button type="button" onClick={() => navigate(`/products/${p.id}/edit`)}>
                                ✎ Sửa
                            </button>
                            {p.status === 'ACTIVE' ? (
                                <button type="button" onClick={() => handlePublishToggle(p)}>
                                    Unpublish
                                </button>
                            ) : (
                                <button type="button" onClick={() => handlePublishToggle(p)}>
                                    Publish
                                </button>
                            )}
                        </>
                    )}
                    {canDelete && (
                        <button type="button" className="danger" onClick={() => handleDelete(p)}>
                            🗑 Xoá
                        </button>
                    )}
                </div>
            ),
        },
    ];

    return (
        <div className="admin-page product-list-page">
            <header className="admin-page__header">
                <div>
                    <h1>Sản phẩm</h1>
                    <p className="admin-page__sub">{totalElements} sản phẩm</p>
                </div>
                {canCreate && (
                    <Button variant="primary" size="md" onClick={() => navigate('/products/new')}>
                        + Thêm sản phẩm
                    </Button>
                )}
            </header>

            {/* -------- Filters -------- */}
            <section className="product-list-page__filters">
                <FormField label="Tìm kiếm" className="product-list-page__search">
                    <input
                        type="search"
                        placeholder="Tên, SKU…"
                        value={filters.keyword || ''}
                        onChange={(e) => setFilters({ keyword: e.target.value })}
                    />
                </FormField>

                <FormField label="Trạng thái" htmlFor="filter-status">
                    <select
                        id="filter-status"
                        value={filters.status || ''}
                        onChange={(e) => setFilters({ status: e.target.value })}
                    >
                        <option value="">Tất cả</option>
                        <option value="DRAFT">Bản nháp</option>
                        <option value="ACTIVE">Đang bán</option>
                        <option value="OUT_OF_STOCK">Hết hàng</option>
                        <option value="DISCONTINUED">Ngừng bán</option>
                    </select>
                </FormField>

                <FormField label="Danh mục" htmlFor="filter-category">
                    <select
                        id="filter-category"
                        value={filters.category || ''}
                        onChange={(e) => setFilters({ category: e.target.value })}
                    >
                        <option value="">Tất cả</option>
                        {categoriesFlat.map((c) => (
                            <option key={c.id} value={c.slug || c.id}>
                                {c.name}
                            </option>
                        ))}
                    </select>
                </FormField>
            </section>

            {/* -------- Table -------- */}
            {error ? (
                <div className="product-list-page__error">
                    <strong>Không thể tải sản phẩm.</strong> {error?.message}
                    <button type="button" onClick={refresh}>Thử lại</button>
                </div>
            ) : (
                <>
                    <DataTable
                        columns={columns}
                        rows={items}
                        loading={loading}
                        rowKey="id"
                        emptyText="Chưa có sản phẩm nào."
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