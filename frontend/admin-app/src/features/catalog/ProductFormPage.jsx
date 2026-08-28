import { useEffect, useMemo, useState, useRef } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { adminCatalogApi } from '../../services/apiAdminCatalog.js';
import { storageApi } from '../../services/apiStorage.js';
import { useCategoriesFlat } from '../../hooks/useAdminCategories.js';
import { useAdminMaterials } from '../../hooks/useAdminMaterials.js';
import { Button, FormField } from '../../components/index.js';
import {
    ENVIRONMENTS, ROOMS, PRODUCT_STATUSES,
} from '../../utils/catalogMeta.js';
import './ProductFormPage.css';

/**
 * ProductFormPage
 *
 * Full schema coverage (matches ProductRequest in backend):
 *   - sku (required, unique)
 *   - name (required, 3-200)
 *   - slug (optional, server auto-generates from name if blank)
 *   - categoryId (required)
 *   - materialIds (multi-select via checkboxes — backend stores ids)
 *   - environment (required, enum)
 *   - room (optional, enum)
 *   - dimensions (width/height/depth cm — BigDecimal)
 *   - weight (kg, > 0)
 *   - color, finish (free text)
 *   - price (required, > 0)
 *   - salePrice (>= 0)
 *   - images (URL list — uploaded via backend /storage/upload endpoint)
 *   - description, warranty
 *   - status (string — DRAFT/ACTIVE/…)
 *
 * Validation mirrors @Valid rules in ProductRequest.java.
 */
export default function ProductFormPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const isEdit = !!id;

    usePageTitle(isEdit ? 'Sửa sản phẩm' : 'Thêm sản phẩm');

    const [form, setForm] = useState(emptyForm());
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);
    const [initialLoading, setInitialLoading] = useState(isEdit);
    const [loadError, setLoadError] = useState(null);

    const { data: categories } = useCategoriesFlat();
    const { data: materials } = useAdminMaterials();

    // -------- fetch existing product if editing --------
    useEffect(() => {
        if (!isEdit) return;
        let cancelled = false;
        setLoadError(null);
        adminCatalogApi
            .getProductById(id)
            .then((p) => {
                if (cancelled) return;
                setForm({
                    sku: p.sku || '',
                    name: p.name || '',
                    slug: p.slug || '',
                    categoryId: p.categoryId || '',
                    materialIds: p.materialIds || [],
                    environment: p.environment || 'INDOOR',
                    room: p.room || '',
                    dimensions: {
                        width: p.dimensions?.width ?? '',
                        height: p.dimensions?.height ?? '',
                        depth: p.dimensions?.depth ?? '',
                    },
                    weight: p.weight ?? '',
                    color: p.color || '',
                    finish: p.finish || '',
                    price: p.price ?? '',
                    salePrice: p.salePrice ?? '',
                    images: p.images || [],
                    description: p.description || '',
                    warranty: p.warranty || '',
                    status: p.status || 'DRAFT',
                });
                setInitialLoading(false);
            })
            .catch((err) => {
                if (cancelled) return;
                setLoadError(err);
                setInitialLoading(false);
            });
        return () => { cancelled = true; };
    }, [id, isEdit]);

    // -------- field handlers --------
    const setField = (key) => (e) => {
        const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
        setForm((prev) => ({ ...prev, [key]: value }));
        if (errors[key]) setErrors((prev) => ({ ...prev, [key]: null }));
    };
    const setDim = (key) => (e) => {
        const value = e.target.value;
        setForm((prev) => ({ ...prev, dimensions: { ...prev.dimensions, [key]: value } }));
        if (errors[`dimensions.${key}`]) setErrors((prev) => ({ ...prev, [`dimensions.${key}`]: null }));
    };
    const toggleMaterial = (materialId) => {
        setForm((prev) => ({
            ...prev,
            materialIds: prev.materialIds.includes(materialId)
                ? prev.materialIds.filter((x) => x !== materialId)
                : [...prev.materialIds, materialId],
        }));
    };

    // -------- validate (mirrors backend ProductRequest rules) --------
    const validate = () => {
        const next = {};
        if (!form.sku.trim()) next.sku = 'SKU là bắt buộc';
        if (!form.name.trim()) next.name = 'Tên sản phẩm là bắt buộc';
        else if (form.name.trim().length < 3 || form.name.trim().length > 200) {
            next.name = 'Tên phải từ 3 đến 200 ký tự';
        }
        if (!form.categoryId) next.categoryId = 'Danh mục là bắt buộc';
        if (!form.environment) next.environment = 'Môi trường là bắt buộc';
        if (!form.price) next.price = 'Giá là bắt buộc';
        else {
            const p = Number(form.price);
            if (!Number.isFinite(p) || p <= 0) next.price = 'Giá phải > 0';
        }
        if (form.salePrice !== '' && form.salePrice != null) {
            const sp = Number(form.salePrice);
            if (!Number.isFinite(sp) || sp < 0) next.salePrice = 'Giá khuyến mãi không được âm';
            else if (Number(form.price) > 0 && sp >= Number(form.price)) {
                next.salePrice = 'Giá khuyến mãi phải nhỏ hơn giá gốc';
            }
        }
        if (form.weight !== '' && form.weight != null) {
            const w = Number(form.weight);
            if (!Number.isFinite(w) || w <= 0) next.weight = 'Khối lượng phải > 0';
        }
        if (form.status === 'ACTIVE' && (!form.images || form.images.length === 0)) {
            next.images = 'Sản phẩm ACTIVE phải có ít nhất 1 ảnh';
        }
        return next;
    };

    // -------- submit --------
    const handleSubmit = async (e) => {
        e.preventDefault();
        const next = validate();
        if (Object.keys(next).length > 0) {
            setErrors(next);
            toast.error('Vui lòng kiểm tra các trường lỗi');
            return;
        }
        setErrors({});
        setSubmitting(true);

        const payload = {
            sku: form.sku.trim(),
            name: form.name.trim(),
            slug: form.slug.trim() || undefined,
            categoryId: form.categoryId,
            materialIds: form.materialIds,
            environment: form.environment,
            room: form.room || undefined,
            dimensions: {
                width: toNum(form.dimensions.width),
                height: toNum(form.dimensions.height),
                depth: toNum(form.dimensions.depth),
            },
            weight: form.weight === '' ? undefined : toNum(form.weight),
            color: form.color.trim() || undefined,
            finish: form.finish.trim() || undefined,
            price: toNum(form.price),
            salePrice: form.salePrice === '' ? undefined : toNum(form.salePrice),
            images: form.images,
            description: form.description.trim() || undefined,
            warranty: form.warranty.trim() || undefined,
            status: form.status,
        };

        try {
            const saved = isEdit
                ? await adminCatalogApi.updateProduct(id, payload)
                : await adminCatalogApi.createProduct(payload);
            toast.success(isEdit ? 'Đã cập nhật sản phẩm' : 'Đã tạo sản phẩm');
            navigate('/products');
        } catch (err) {
            // Server-side validation errors come back as { errors: { field: msg } }
            // Map them into our local state for inline display.
            if (err?.errors && typeof err.errors === 'object') {
                setErrors(err.errors);
            }
        } finally {
            setSubmitting(false);
        }
    };

    if (initialLoading) return <div className="admin-page">Đang tải...</div>;
    if (loadError) {
        return (
            <div className="admin-page">
                <div className="product-form__error">Không thể tải sản phẩm: {loadError?.message}</div>
                <Link to="/products">← Quay lại</Link>
            </div>
        );
    }

    return (
        <div className="admin-page product-form">
            <header className="admin-page__header">
                <div>
                    <h1>{isEdit ? 'Sửa sản phẩm' : 'Thêm sản phẩm mới'}</h1>
                    <p className="admin-page__sub">
                        {isEdit ? `ID: ${id}` : 'Sản phẩm mới sẽ tạo với trạng thái DRAFT.'}
                    </p>
                </div>
                <div className="product-form__topbar-actions">
                    <Link to="/products" className="product-form__cancel">← Huỷ</Link>
                </div>
            </header>

            <form className="product-form__grid" onSubmit={handleSubmit} noValidate>
                {/* ===== Column 1 ===== */}
                <section className="product-form__section">
                    <h2>Thông tin cơ bản</h2>

                    <div className="product-form__row">
                        <FormField label="SKU" required htmlFor="sku" error={errors.sku}>
                            <input id="sku" value={form.sku} onChange={setField('sku')} placeholder="VD: WFN-TBL-001" />
                        </FormField>
                        <FormField label="Slug (URL)" htmlFor="slug" hint="Để trống = tự sinh từ tên" error={errors.slug}>
                            <input id="slug" value={form.slug} onChange={setField('slug')} placeholder="ban-go-soi" />
                        </FormField>
                    </div>

                    <FormField label="Tên sản phẩm" required htmlFor="name" error={errors.name} hint="3–200 ký tự">
                        <input id="name" value={form.name} onChange={setField('name')} />
                    </FormField>

                    <div className="product-form__row">
                        <FormField label="Danh mục" required htmlFor="categoryId" error={errors.categoryId}>
                            <select id="categoryId" value={form.categoryId} onChange={setField('categoryId')}>
                                <option value="">-- Chọn --</option>
                                {categories.map((c) => (
                                    <option key={c.id} value={c.id}>{c.name}</option>
                                ))}
                            </select>
                        </FormField>

                        <FormField label="Trạng thái" htmlFor="status">
                            <select id="status" value={form.status} onChange={setField('status')}>
                                {PRODUCT_STATUSES.map((s) => (
                                    <option key={s.value} value={s.value}>{s.label}</option>
                                ))}
                            </select>
                        </FormField>
                    </div>

                    <FormField label="Mô tả" htmlFor="description">
                        <textarea id="description" rows={5} value={form.description} onChange={setField('description')} />
                    </FormField>

                    <FormField label="Bảo hành" htmlFor="warranty" hint="VD: 12 tháng">
                        <input id="warranty" value={form.warranty} onChange={setField('warranty')} />
                    </FormField>
                </section>

                {/* ===== Column 2 ===== */}
                <section className="product-form__section">
                    <h2>Phân loại & Kích thước</h2>

                    <div className="product-form__row">
                        <FormField label="Môi trường" required htmlFor="environment" error={errors.environment}>
                            <select id="environment" value={form.environment} onChange={setField('environment')}>
                                {ENVIRONMENTS.map((e) => (
                                    <option key={e.value} value={e.value}>{e.label}</option>
                                ))}
                            </select>
                        </FormField>
                        <FormField label="Phòng / Khu vực" htmlFor="room">
                            <select id="room" value={form.room} onChange={setField('room')}>
                                <option value="">-- Không --</option>
                                {ROOMS.map((r) => (
                                    <option key={r.value} value={r.value}>{r.label}</option>
                                ))}
                            </select>
                        </FormField>
                    </div>

                    <fieldset className="product-form__group">
                        <legend>Kích thước (cm)</legend>
                        <div className="product-form__row">
                            <FormField label="Rộng" error={errors['dimensions.width']}>
                                <input type="number" min="0" step="0.1" value={form.dimensions.width} onChange={setDim('width')} />
                            </FormField>
                            <FormField label="Cao" error={errors['dimensions.height']}>
                                <input type="number" min="0" step="0.1" value={form.dimensions.height} onChange={setDim('height')} />
                            </FormField>
                            <FormField label="Sâu" error={errors['dimensions.depth']}>
                                <input type="number" min="0" step="0.1" value={form.dimensions.depth} onChange={setDim('depth')} />
                            </FormField>
                        </div>
                    </fieldset>

                    <div className="product-form__row">
                        <FormField label="Khối lượng (kg)" htmlFor="weight" error={errors.weight}>
                            <input id="weight" type="number" min="0" step="0.1" value={form.weight} onChange={setField('weight')} />
                        </FormField>
                        <FormField label="Màu sắc" htmlFor="color">
                            <input id="color" value={form.color} onChange={setField('color')} placeholder="VD: Nâu tự nhiên" />
                        </FormField>
                    </div>

                    <FormField label="Hoàn thiện" htmlFor="finish">
                        <input id="finish" value={form.finish} onChange={setField('finish')} placeholder="VD: Bóng mờ" />
                    </FormField>
                </section>

                {/* ===== Column 3 ===== */}
                <section className="product-form__section">
                    <h2>Giá</h2>
                    <div className="product-form__row">
                        <FormField label="Giá gốc (VND)" required htmlFor="price" error={errors.price} hint="Phải lớn hơn 0">
                            <input id="price" type="number" min="0" step="1000" value={form.price} onChange={setField('price')} />
                        </FormField>
                        <FormField label="Giá khuyến mãi (VND)" htmlFor="salePrice" error={errors.salePrice} hint="Không bắt buộc, phải < giá gốc">
                            <input id="salePrice" type="number" min="0" step="1000" value={form.salePrice} onChange={setField('salePrice')} />
                        </FormField>
                    </div>

                    <h2>Loại gỗ (vật liệu)</h2>
                    <FormField label="Chọn vật liệu">
                        <div className="product-form__chips">
                            {materials.length === 0 && <p className="product-form__hint">Chưa có vật liệu nào trong hệ thống.</p>}
                            {materials.map((m) => (
                                <label key={m.id} className={`chip ${form.materialIds.includes(m.id) ? 'is-on' : ''}`}>
                                    <input
                                        type="checkbox"
                                        checked={form.materialIds.includes(m.id)}
                                        onChange={() => toggleMaterial(m.id)}
                                    />
                                    <span>{m.name} <small>({m.code})</small></span>
                                </label>
                            ))}
                        </div>
                    </FormField>
                </section>

                {/* ===== Column 4 ===== */}
                <section className="product-form__section product-form__section--wide">
                    <h2>Hình ảnh</h2>
                    <p className="product-form__hint">
                        Dùng nút "Chọn ảnh từ máy tính" để upload, hoặc dán URL trực tiếp.
                        Ảnh đầu tiên sẽ là ảnh đại diện của sản phẩm.
                    </p>
                    <ImagesEditor
                        images={form.images}
                        onChange={(next) => {
                            setForm((prev) => ({ ...prev, images: next }));
                            if (errors.images) setErrors((prev) => ({ ...prev, images: null }));
                        }}
                        error={errors.images}
                    />
                </section>

                {/* ===== Submit ===== */}
                <div className="product-form__actions">
                    <Link to="/products" className="product-form__btn-cancel">Huỷ</Link>
                    <Button type="submit" variant="primary" size="md" loading={submitting}>
                        {isEdit ? 'Lưu thay đổi' : 'Tạo sản phẩm'}
                    </Button>
                </div>
            </form>
        </div>
    );
}

// =============================================================
// ImagesEditor — supports both URL paste and file upload
// =============================================================

function ImagesEditor({ images, onChange, error }) {
    const [draft, setDraft] = useState('');
    const [uploading, setUploading] = useState(false);

    // ---- paste URL ----
    const addUrl = () => {
        const url = draft.trim();
        if (!url) return;
        if (!/^https?:\/\//i.test(url)) {
            toast.error('URL không hợp lệ (phải bắt đầu với http:// hoặc https://)');
            return;
        }
        onChange([...images, url]);
        setDraft('');
    };

    // ---- file upload ----
    const handleFileSelect = async (e) => {
        const files = Array.from(e.target.files);
        if (files.length === 0) return;

        setUploading(true);
        try {
            const urls = await storageApi.uploadImages(files);
            onChange([...images, ...urls]);
            toast.success(`Đã tải lên ${urls.length} ảnh`);
        } catch (err) {
            toast.error('Tải ảnh thất bại: ' + (err?.message || 'Lỗi không xác định'));
        } finally {
            setUploading(false);
            // Reset file input so same file can be re-selected
            e.target.value = '';
        }
    };

    const removeImage = (i) => onChange(images.filter((_, idx) => idx !== i));
    const moveUp = (i) => {
        if (i === 0) return;
        const next = [...images];
        [next[i - 1], next[i]] = [next[i], next[i - 1]];
        onChange(next);
    };
    const moveDown = (i) => {
        if (i === images.length - 1) return;
        const next = [...images];
        [next[i + 1], next[i]] = [next[i], next[i + 1]];
        onChange(next);
    };

    return (
        <div>
            {error && <p className="product-form__error-text">{error}</p>}

            {/* URL input */}
            <div className="product-form__image-input">
                <input
                    type="url"
                    placeholder="https://example.com/image.jpg"
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addUrl(); } }}
                />
                <button type="button" onClick={addUrl} className="product-form__add-btn">+ Thêm URL</button>
            </div>

            {/* File upload */}
            <div className="product-form__upload-area">
                <label className="product-form__upload-btn" aria-disabled={uploading}>
                    {uploading ? 'Đang tải lên...' : '📁 Chọn ảnh từ máy tính'}
                    <input
                        type="file"
                        accept="image/jpeg,image/png,image/gif,image/webp"
                        multiple
                        style={{ display: 'none' }}
                        onChange={handleFileSelect}
                        disabled={uploading}
                    />
                </label>
                <span className="product-form__hint">JPEG, PNG, GIF, WEBP — tối đa 5 MB mỗi file</span>
            </div>

            {/* Image list */}
            {images.length > 0 && (
                <ul className="product-form__images">
                    {images.map((src, i) => (
                        <li key={i} className="product-form__image-item">
                            <img src={src} alt="" onError={(e) => { e.currentTarget.src = '/placeholder-product.svg'; }} />
                            <div className="product-form__image-meta">
                                <span className="product-form__image-index">Ảnh {i + 1}</span>
                                <code>{truncate(src, 60)}</code>
                            </div>
                            <div className="product-form__image-actions">
                                <button type="button" onClick={() => moveUp(i)} disabled={i === 0}>↑</button>
                                <button type="button" onClick={() => moveDown(i)} disabled={i === images.length - 1}>↓</button>
                                <button type="button" className="danger" onClick={() => removeImage(i)}>✕</button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

// =============================================================
// helpers
// =============================================================

function emptyForm() {
    return {
        sku: '',
        name: '',
        slug: '',
        categoryId: '',
        materialIds: [],
        environment: 'INDOOR',
        room: '',
        dimensions: { width: '', height: '', depth: '' },
        weight: '',
        color: '',
        finish: '',
        price: '',
        salePrice: '',
        images: [],
        description: '',
        warranty: '',
        status: 'DRAFT',
    };
}

function toNum(v) {
    if (v === '' || v === null || v === undefined) return undefined;
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
}

function truncate(s, n) {
    if (!s) return '';
    return s.length > n ? `${s.slice(0, n - 1)}…` : s;
}