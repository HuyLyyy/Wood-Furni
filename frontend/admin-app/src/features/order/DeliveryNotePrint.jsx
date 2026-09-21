import { formatCurrency, formatDateTime } from '../../utils/format.js';

/**
 * DeliveryNotePrint
 *
 * Renders the delivery note (phiếu giao hàng) in a format optimised for
 * printing.  Designed for A4 portrait at standard browser print scale.
 *
 * This component is intentionally unstyled for screen — it is only ever
 * rendered inside a hidden iframe for printing.  All styles are inline so
 * they survive the iframe render without requiring an external stylesheet.
 *
 * Usage:
 *   <DeliveryNotePrint order={order} ref={printRef} />
 *
 * Then call printRef.current.contentWindow.print() from the parent.
 */
const DeliveryNotePrint = ({ order }) => {
    if (!order) return null;

    const items = order.items || [];
    const subtotal = order.subtotalAmount
        ?? items.reduce((s, it) => s + Number(it.subtotal || 0), 0);
    const discount = Number(order.discountAmount || 0);
    const total = Number(order.totalAmount || 0);
    const addr = order.shippingAddress || {};

    const printDate = new Date().toLocaleString('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });

    const orderDate = order.createdAt
        ? formatDateTime(order.createdAt)
        : '—';

    const itemRows = items.map((it, i) => (
        <tr key={`${it.productId}-${i}`}>
            <td style={cell(40, 'center')}>{i + 1}</td>
            <td style={cell(250)}>{it.productName || '—'}</td>
            <td style={cell(80, 'center')}>{it.sku || '—'}</td>
            <td style={cell(60, 'center')}>{it.quantity}</td>
            <td style={cell(90, 'right')}>{formatCurrency(it.unitPrice)}</td>
            <td style={cell(100, 'right')}>{formatCurrency(it.subtotal)}</td>
        </tr>
    ));

    return (
        <div style={doc()}>
            {/* ── Header ── */}
            <div style={headerBlock()}>
                <div style={brandRow()}>
                    <span style={brandMark()}>W</span>
                    <div>
                        <div style={brandName()}>WOODFURNI</div>
                        <div style={brandSub()}>Nội thất gỗ cao cấp</div>
                    </div>
                </div>
                <div style={docTitle()}>PHIẾU GIAO HÀNG</div>
            </div>

            {/* ── Meta ── */}
            <div style={metaGrid()}>
                <div style={metaCol()}>
                    <InfoRow label="Mã đơn hàng" value={order.orderNumber || order.id} bold />
                    <InfoRow label="Ngày đặt" value={orderDate} />
                    <InfoRow label="Phương thức TT" value={order.paymentMethod || '—'} />
                </div>
                <div style={metaCol()}>
                    <InfoRow label="Ngày in" value={printDate} />
                    <InfoRow label="Trạng thái" value={order.status || '—'} />
                    <InfoRow label="Thanh toán" value={order.paymentStatus || '—'} />
                </div>
            </div>

            <hr style={hr()} />

            {/* ── Customer ── */}
            <div style={section()}>
                <div style={sectionTitle()}>THÔNG TIN GIAO HÀNG</div>
                <div style={infoGrid()}>
                    <div style={infoCol()}>
                        <div style={fieldLabel()}>Khách hàng</div>
                        <div style={fieldValue()}>{order.customerName || addr.label || '—'}</div>
                    </div>
                    <div style={infoCol()}>
                        <div style={fieldLabel()}>Số điện thoại</div>
                        <div style={fieldValue()}>{addr.phone || '—'}</div>
                    </div>
                </div>
                <div style={infoFull()}>
                    <div style={fieldLabel()}>Địa chỉ giao hàng</div>
                    <div style={fieldValue()}>
                        {[addr.line1, addr.ward, addr.district, addr.city]
                            .filter(Boolean)
                            .join(', ') || '—'}
                    </div>
                </div>
                {order.note && (
                    <div style={infoFull()}>
                        <div style={fieldLabel()}>Ghi chú</div>
                        <div style={{ ...fieldValue(), fontStyle: 'italic' }}>{order.note}</div>
                    </div>
                )}
            </div>

            {/* ── Items ── */}
            <div style={section()}>
                <div style={sectionTitle()}>DANH SÁCH SẢN PHẨM</div>
                <table style={table()}>
                    <thead>
                        <tr>
                            <th style={th(40, 'center')}>#</th>
                            <th style={th(250)}>Tên sản phẩm</th>
                            <th style={th(80, 'center')}>SKU</th>
                            <th style={th(60, 'center')}>SL</th>
                            <th style={th(90, 'right')}>Đơn giá</th>
                            <th style={th(100, 'right')}>Thành tiền</th>
                        </tr>
                    </thead>
                    <tbody>{itemRows}</tbody>
                </table>
            </div>

            {/* ── Totals ── */}
            <div style={totalsBlock()}>
                <div style={totalRow()}>
                    <span>Tạm tính</span>
                    <span style={totalVal()}>{formatCurrency(subtotal)}</span>
                </div>
                {discount > 0 && (
                    <div style={totalRow()}>
                        <span>Giảm giá{order.promotionCode ? ` (${order.promotionCode})` : ''}</span>
                        <span style={{ ...totalVal(), color: '#16a34a' }}>−{formatCurrency(discount)}</span>
                    </div>
                )}
                <div style={{ ...totalRow(), borderTop: '2px solid #2b2a27', paddingTop: 8, marginTop: 4 }}>
                    <span style={grandLabel()}>TỔNG CỘNG</span>
                    <span style={grandVal()}>{formatCurrency(total)}</span>
                </div>
            </div>

            {/* ── Signatures ── */}
            <div style={signatures()}>
                <div style={sigBox()}>
                    <div style={sigTitle()}>Người giao hàng</div>
                    <div style={sigLine()}>Ký và ghi rõ họ tên</div>
                </div>
                <div style={sigBox()}>
                    <div style={sigTitle()}>Người nhận hàng</div>
                    <div style={sigLine()}>Ký và ghi rõ họ tên</div>
                </div>
            </div>

            <div style={footer()}>
                <span>WOODFURNI — Nội thất gỗ cao cấp</span>
                <span>Hotline: 1900 xxxx</span>
            </div>
        </div>
    );
};

export default DeliveryNotePrint;

// ── Inline style helpers ───────────────────────────────────────────────────────

const doc = () => ({
    fontFamily: '"Segoe UI", Arial, sans-serif',
    fontSize: 13,
    color: '#2b2a27',
    padding: '32px 40px',
    width: 700,
    boxSizing: 'border-box',
    background: '#fff',
});

const headerBlock = () => ({
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 20,
});

const brandRow = () => ({
    display: 'flex',
    alignItems: 'center',
    gap: 10,
});

const brandMark = () => ({
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 44,
    height: 44,
    background: '#5a3a22',
    color: '#fff',
    borderRadius: 8,
    fontSize: 22,
    fontWeight: 700,
});

const brandName = () => ({
    fontSize: 18,
    fontWeight: 700,
    color: '#5a3a22',
    letterSpacing: 2,
});

const brandSub = () => ({
    fontSize: 11,
    color: '#7a6a5a',
    marginTop: 2,
});

const docTitle = () => ({
    fontSize: 22,
    fontWeight: 700,
    color: '#5a3a22',
    letterSpacing: 1,
    textAlign: 'right',
    lineHeight: 1.2,
});

const metaGrid = () => ({
    display: 'flex',
    gap: 40,
    marginBottom: 16,
});

const metaCol = () => ({
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
});

const hr = () => ({
    border: 'none',
    borderTop: '1px dashed #c9c0b4',
    margin: '12px 0',
});

const section = () => ({
    marginBottom: 20,
});

const sectionTitle = () => ({
    fontSize: 11,
    fontWeight: 700,
    color: '#7a6a5a',
    letterSpacing: 1.5,
    marginBottom: 8,
    textTransform: 'uppercase',
});

const infoGrid = () => ({
    display: 'flex',
    gap: 40,
    marginBottom: 8,
});

const infoCol = () => ({
    flex: 1,
});

const infoFull = () => ({
    marginBottom: 8,
});

const fieldLabel = () => ({
    fontSize: 11,
    color: '#7a6a5a',
    marginBottom: 2,
});

const fieldValue = () => ({
    fontSize: 13,
    fontWeight: 500,
    color: '#2b2a27',
});

const table = () => ({
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: 12,
});

const th = (width, align = 'left') => ({
    width,
    textAlign: align,
    padding: '6px 8px',
    borderBottom: '1px solid #5a3a22',
    color: '#5a3a22',
    fontWeight: 700,
    fontSize: 11,
    letterSpacing: 0.5,
});

const cell = (width, align = 'left') => ({
    width,
    textAlign: align,
    padding: '5px 8px',
    borderBottom: '1px solid #e8e0d8',
    verticalAlign: 'middle',
});

const totalsBlock = () => ({
    marginLeft: 'auto',
    width: 280,
    marginBottom: 24,
});

const totalRow = () => ({
    display: 'flex',
    justifyContent: 'space-between',
    padding: '4px 0',
    fontSize: 13,
});

const totalVal = () => ({
    fontWeight: 600,
});

const grandLabel = () => ({
    fontSize: 14,
    fontWeight: 700,
    color: '#5a3a22',
});

const grandVal = () => ({
    fontSize: 16,
    fontWeight: 700,
    color: '#5a3a22',
});

const signatures = () => ({
    display: 'flex',
    gap: 40,
    marginTop: 8,
    marginBottom: 24,
});

const sigBox = () => ({
    flex: 1,
    textAlign: 'center',
});

const sigTitle = () => ({
    fontSize: 12,
    fontWeight: 700,
    marginBottom: 48,
});

const sigLine = () => ({
    fontSize: 11,
    color: '#7a6a5a',
    borderTop: '1px solid #c9c0b4',
    paddingTop: 4,
});

const footer = () => ({
    textAlign: 'center',
    fontSize: 11,
    color: '#a09080',
    borderTop: '1px solid #e8e0d8',
    paddingTop: 10,
    display: 'flex',
    justifyContent: 'space-between',
});

// InfoRow helper (inline so this file stays dependency-free)
function InfoRow({ label, value, bold }) {
    return (
        <div style={{ display: 'flex', gap: 8, fontSize: 12 }}>
            <span style={{ color: '#7a6a5a', minWidth: 110 }}>{label}:</span>
            <span style={{ fontWeight: bold ? 700 : 400 }}>{value}</span>
        </div>
    );
}
