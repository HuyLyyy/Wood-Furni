import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { listPrintTemplates, downloadPrintTemplate } from '../../services/apiPrintSlips.js';
import './PrintSlipsPage.css';

/**
 * PrintSlipsPage — trang "Phiếu in".
 *
 * Hiển thị một card cho mỗi loại phiếu in (nhập kho, xuất kho, hư hỏng,
 * mất mát, trả lại, chênh lệch kiểm kê, thanh lý). Mỗi card có nút
 * "Tải về Excel" — khi bấm sẽ gọi API download template và trigger save
 * file trên trình duyệt.
 *
 * Luồng nghiệp vụ:
 *   1. Warehouse/Admin vào đây, tải file Excel template trống.
 *   2. In file ra giấy (hoặc mở file .xlsx điền trực tiếp).
 *   3. Điền thông tin bằng tay: mã SP, tên SP, số lượng, giá thành, ghi chú.
 *   4. Vào Kho hàng → điều chỉnh tồn kho → upload file đó làm evidence.
 *
 * Phân quyền: ADMIN / WAREHOUSE (khớp với backend @PreAuthorize).
 */
export default function PrintSlipsPage() {
    usePageTitle('Phiếu in');

    const [types, setTypes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [downloadingCode, setDownloadingCode] = useState(null);

    // Load types once on mount.
    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const list = await listPrintTemplates();
                if (!cancelled) setTypes(list || []);
            } catch (err) {
                if (!cancelled) {
                    toast.error(err?.message || 'Không thể tải danh sách phiếu in');
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    const handleDownload = useCallback(async (typeCode) => {
        setDownloadingCode(typeCode);
        try {
            await downloadPrintTemplate(typeCode);
            toast.success(`Đã tải phiếu ${typeCode}`);
        } catch (err) {
            toast.error(err?.message || 'Tải phiếu thất bại');
        } finally {
            setDownloadingCode(null);
        }
    }, []);

    if (loading) {
        return (
            <div className="print-slips-page">
                <div className="print-slips-page__loading">Đang tải…</div>
            </div>
        );
    }

    return (
        <div className="print-slips-page">
            <header className="print-slips-page__header">
                <h1 className="print-slips-page__title">Phiếu in kho</h1>
                <p className="print-slips-page__subtitle">
                    Tải file Excel trống để in ra giấy, điền thông tin bằng tay
                    (mã sản phẩm, tên sản phẩm, số lượng, giá thành, ghi chú),
                    rồi upload lại làm minh chứng trong mục điều chỉnh tồn kho.
                </p>
            </header>

            <div className="print-slips-page__grid">
                {types.map((t) => {
                    const isDownloading = downloadingCode === t.code;
                    return (
                        <article key={t.code} className="print-slip-card">
                            <div className="print-slip-card__icon" aria-hidden="true">🖨️</div>
                            <h2 className="print-slip-card__title">{t.displayName}</h2>
                            <p className="print-slip-card__meta">
                                Mã loại: <code>{t.code}</code>
                            </p>
                            <button
                                type="button"
                                className="print-slip-card__btn"
                                onClick={() => handleDownload(t.code)}
                                disabled={isDownloading}
                            >
                                {isDownloading ? 'Đang tải…' : 'Tải về Excel'}
                            </button>
                        </article>
                    );
                })}
            </div>
        </div>
    );
}
