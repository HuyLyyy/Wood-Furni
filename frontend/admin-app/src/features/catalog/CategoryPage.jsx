import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { adminCatalogApi } from '../../services/apiAdminCatalog.js';
import { useCategories, useCategoriesFlat } from '../../hooks/useAdminCategories.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { Button, FormField, useConfirmDialog } from '../../components/index.js';
import {
    CATEGORY_ENVIRONMENTS, CATEGORY_STATUSES,
    categoryEnvLabel, categoryStatus,
} from '../../utils/catalogMeta.js';
import { can } from '../../utils/permissions.js';
import './CategoryPage.css';

/**
 * CategoryPage
 *
 *   - Tree view (CategoryTreeResponse) left, edit panel right.
 *   - Add / Edit / Delete with the same field set as CategoryRequest.
 *   - Drag-to-reorder is NOT required (per spec), skipped.
 *   - Backend roles: CONTENT/ADMIN for create/update, ADMIN only for delete.
 */
export default function CategoryPage() {
    usePageTitle('Danh mục');
    const { user } = useAuth();
    const role = user?.role;
    const canCreate = can(role, 'categories:create');
    const canEdit   = can(role, 'categories:update');
    const canDelete = can(role, 'categories:delete');

    const { data: tree, loading, error, refresh } = useCategories();
    const { data: flat } = useCategoriesFlat();
    const { confirm, dialog } = useConfirmDialog();

    const [selectedId, setSelectedId] = useState(null);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editorMode, setEditorMode] = useState('create'); // 'create' | 'edit'
    const [draft, setDraft] = useState(emptyDraft());
    const [saving, setSaving] = useState(false);

    // When editing, draft is built from the existing record.
    const targetForEdit = selectedId ? findById(tree, selectedId) : null;

    useEffect(() => {
        if (editorMode === 'edit' && targetForEdit) {
            setDraft({
                name: targetForEdit.name || '',
                slug: targetForEdit.slug || '',
                environment: targetForEdit.environment || 'INDOOR',
                parentId: targetForEdit.parentId || '',
                order: targetForEdit.order ?? 0,
                status: targetForEdit.status || 'ACTIVE',
            });
        }
    }, [editorMode, targetForEdit]);

    const startCreate = () => {
        setEditorMode('create');
        setDraft({
            ...emptyDraft(),
            parentId: selectedId || '', // pre-fill parent if a node is selected
        });
        setEditorOpen(true);
    };
    const startEdit = (node) => {
        setSelectedId(node.id);
        setEditorMode('edit');
        setEditorOpen(true);
    };

    const save = async (e) => {
        e.preventDefault();
        if (!draft.name.trim()) {
            toast.error('Tên danh mục không được trống');
            return;
        }
        if (!draft.environment) {
            toast.error('Môi trường là bắt buộc');
            return;
        }
        const payload = {
            name: draft.name.trim(),
            slug: draft.slug.trim() || undefined,
            environment: draft.environment,
            parentId: draft.parentId || null,
            order: Number(draft.order) || 0,
            status: draft.status || 'ACTIVE',
        };
        setSaving(true);
        try {
            if (editorMode === 'create') {
                await adminCatalogApi.createCategory(payload);
                toast.success('Đã tạo danh mục');
            } else if (selectedId) {
                await adminCatalogApi.updateCategory(selectedId, payload);
                toast.success('Đã cập nhật danh mục');
            }
            setEditorOpen(false);
            refresh();
        } catch { /* toast by interceptor */ }
        finally { setSaving(false); }
    };

    const remove = async (node) => {
        const ok = await confirm({
            title: 'Xoá danh mục?',
            message: `Sẽ xoá "${node.name}".${
                node.children?.length ? ' Danh mục có danh mục con — việc xoá sẽ thất bại ở backend.' : ''
            }`,
            confirmLabel: 'Xoá',
            danger: true,
        });
        if (!ok) return;
        try {
            await adminCatalogApi.deleteCategory(node.id);
            toast.success('Đã xoá');
            setSelectedId(null);
            refresh();
        } catch { /* toast by interceptor */ }
    };

    return (
        <div className="admin-page category-page">
            <header className="admin-page__header">
                <div>
                    <h1>Danh mục sản phẩm</h1>
                    <p className="admin-page__sub">Cây danh mục nội/ngoại thất</p>
                </div>
                {canCreate && (
                    <Button variant="primary" size="md" onClick={startCreate}>+ Thêm danh mục</Button>
                )}
            </header>

            <div className="category-page__grid">
                <aside className="category-page__tree">
                    <h3>Cây danh mục</h3>
                    {loading && <div className="category-page__muted">Đang tải...</div>}
                    {error && <div className="category-page__error">Không thể tải cây danh mục</div>}
                    {!loading && (!tree || tree.length === 0) && (
                        <div className="category-page__muted">Chưa có danh mục nào.</div>
                    )}
                    <ul className="category-tree">
                        {(tree || []).map((node) => (
                            <CategoryNode
                                key={node.id}
                                node={node}
                                depth={0}
                                selectedId={selectedId}
                                onSelect={setSelectedId}
                                onEdit={canEdit ? startEdit : null}
                                onDelete={canDelete ? remove : null}
                            />
                        ))}
                    </ul>
                </aside>

                <section className="category-page__editor">
                    {!editorOpen && (
                        <div className="category-page__empty">
                            <p>Chọn một danh mục ở bên trái để xem chi tiết, hoặc bấm "Thêm danh mục".</p>
                            {selectedId && targetForEdit && (
                                <CategoryDetail node={targetForEdit} flat={flat} />
                            )}
                        </div>
                    )}

                    {editorOpen && (
                        <form onSubmit={save} className="category-page__form">
                            <h3>{editorMode === 'create' ? 'Thêm danh mục' : 'Sửa danh mục'}</h3>

                            <FormField label="Tên" required htmlFor="cat-name">
                                <input id="cat-name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
                            </FormField>

                            <FormField label="Slug" htmlFor="cat-slug" hint="Để trống = tự sinh">
                                <input id="cat-slug" value={draft.slug} onChange={(e) => setDraft({ ...draft, slug: e.target.value })} />
                            </FormField>

                            <div className="category-page__row">
                                <FormField label="Môi trường" required htmlFor="cat-env">
                                    <select
                                        id="cat-env"
                                        value={draft.environment}
                                        onChange={(e) => setDraft({ ...draft, environment: e.target.value })}
                                    >
                                        {CATEGORY_ENVIRONMENTS.map((e) => (
                                            <option key={e.value} value={e.value}>{e.label}</option>
                                        ))}
                                    </select>
                                </FormField>

                                <FormField label="Trạng thái" htmlFor="cat-status">
                                    <select
                                        id="cat-status"
                                        value={draft.status}
                                        onChange={(e) => setDraft({ ...draft, status: e.target.value })}
                                    >
                                        {CATEGORY_STATUSES.map((s) => (
                                            <option key={s.value} value={s.value}>{s.label}</option>
                                        ))}
                                    </select>
                                </FormField>

                                <FormField label="Thứ tự" htmlFor="cat-order">
                                    <input
                                        id="cat-order"
                                        type="number"
                                        value={draft.order}
                                        onChange={(e) => setDraft({ ...draft, order: e.target.value })}
                                    />
                                </FormField>
                            </div>

                            <FormField label="Danh mục cha" htmlFor="cat-parent">
                                <select
                                    id="cat-parent"
                                    value={draft.parentId || ''}
                                    onChange={(e) => setDraft({ ...draft, parentId: e.target.value })}
                                >
                                    <option value="">(root)</option>
                                    {flat
                                        .filter((c) => c.id !== selectedId)
                                        .map((c) => (
                                            <option key={c.id} value={c.id}>{c.name}</option>
                                        ))}
                                </select>
                            </FormField>

                            <div className="category-page__form-actions">
                                <button type="button" className="btn-cancel" onClick={() => setEditorOpen(false)}>Huỷ</button>
                                <Button type="submit" variant="primary" size="md" loading={saving}>
                                    {editorMode === 'create' ? 'Tạo' : 'Lưu'}
                                </Button>
                            </div>
                        </form>
                    )}
                </section>
            </div>

            {dialog}
        </div>
    );
}

// =============================================================
// Recursive tree
// =============================================================

function CategoryNode({ node, depth, selectedId, onSelect, onEdit, onDelete }) {
    return (
        <li className={`category-tree__node ${selectedId === node.id ? 'is-selected' : ''}`}>
            <div className="category-tree__row" style={{ paddingLeft: 12 + depth * 18 }}>
                <button
                    type="button"
                    className="category-tree__name"
                    onClick={() => onSelect(node.id)}
                >
                    {node.name}
                    <span className="category-tree__meta">
                        {categoryEnvLabel(node.environment)} · {categoryStatus(node.status)}
                    </span>
                </button>
                <div className="category-tree__actions">
                    {onEdit && <button type="button" onClick={() => onEdit(node)}>✎</button>}
                    {onDelete && <button type="button" className="danger" onClick={() => onDelete(node)}>✕</button>}
                </div>
            </div>
            {node.children && node.children.length > 0 && (
                <ul className="category-tree__children">
                    {node.children.map((child) => (
                        <CategoryNode
                            key={child.id}
                            node={child}
                            depth={depth + 1}
                            selectedId={selectedId}
                            onSelect={onSelect}
                            onEdit={onEdit}
                            onDelete={onDelete}
                        />
                    ))}
                </ul>
            )}
        </li>
    );
}

function CategoryDetail({ node, flat }) {
    const parent = flat.find((c) => c.id === node.parentId);
    return (
        <dl className="category-detail">
            <dt>ID</dt><dd>{node.id}</dd>
            <dt>Tên</dt><dd>{node.name}</dd>
            <dt>Slug</dt><dd>{node.slug || '—'}</dd>
            <dt>Môi trường</dt><dd>{categoryEnvLabel(node.environment)}</dd>
            <dt>Cha</dt><dd>{parent ? parent.name : '— (root)'}</dd>
            <dt>Thứ tự</dt><dd>{node.order}</dd>
            <dt>Trạng thái</dt><dd>{categoryStatus(node.status)}</dd>
            <dt>Slug</dt><dd>{node.slug}</dd>
        </dl>
    );
}

// =============================================================
// Helpers
// =============================================================

function emptyDraft() {
    return { name: '', slug: '', environment: 'INDOOR', parentId: '', order: 0, status: 'ACTIVE' };
}

function findById(tree, id) {
    for (const n of tree || []) {
        if (n.id === id) return n;
        const found = findById(n.children, id);
        if (found) return found;
    }
    return null;
}