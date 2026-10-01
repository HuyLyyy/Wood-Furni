import { formatDateTime, formatCurrency } from '../../utils/format.js';

/**
 * PaymentReceiptPrint
 *
 * Vietnamese-style payment receipt (biên nhận thu tiền) theo mẫu:
 *   - Mã đơn hàng
 *   - Ngày đơn hàng
 *   - Tên cửa hàng: WOODFURNI
 *   - Tên khách hàng
 *   - Địa chỉ giao hàng
 *   - Khung tên sản phẩm (table: STT | Tên SP | SL | Thành tiền)
 *   - Tổng tiền hàng
 *   - Số lượng sản phẩm
 *   - Hình thức thanh toán
 *   - Số tiền bằng chữ
 *   - Khách hàng ký tên + NVGH ký tên + Kế toán ký tên
 *   - Mã QR chuyển khoản
 *
 * Everything is inline-styled so the layout survives @media print.
 * Designed for A4 portrait.
 */
const PaymentReceiptPrint = ({ order }) => {
    if (!order) return null;

    const total = Number(order.totalAmount || 0);
    const paymentMethod = order.paymentMethod || 'CASH';
    const orderDate = order.createdAt ? formatDateTime(order.createdAt) : '—';
    const orderNumber = order.orderNumber || order.id || '—';
    const customerName = order.customerName || order.shippingAddress?.label || '—';
    const customerAddr = [
        order.shippingAddress?.line1,
        order.shippingAddress?.ward,
        order.shippingAddress?.district,
        order.shippingAddress?.city,
    ].filter(Boolean).join(', ') || '—';

    const items = Array.isArray(order.items) ? order.items : [];
    const totalQuantity = items.reduce((s, it) => s + Number(it.quantity || 0), 0);

    const amountInWords = numberToVietnameseText(total);

    return (
        <div style={page()}>
            <div style={frame()}>
                {/* ── Top row: Title + QR ─────────────────────────── */}
                <div style={topRow()}>
                    <div style={{ flex: 1 }}>
                        <div style={storeName()}>WOODFURNI</div>
                        <div style={storeSub()}>Nội thất gỗ cao cấp</div>
                    </div>
                    <div style={titleCol()}>
                        <div style={title()}>BIÊN NHẬN THANH TOÁN</div>
                        <div style={titleSub()}>
                            Ngày {extractDay(orderDate)} tháng {extractMonth(orderDate)} năm {extractYear(orderDate)}
                        </div>
                    </div>
                    <div style={qrCol()}>
                        <img
                            src={QR_SRC}
                            alt="Mã QR chuyển khoản"
                            style={qrImg()}
                            onError={(e) => { e.currentTarget.style.display = 'none'; }}
                        />
                        <div style={qrCaption()}>Quét để chuyển khoản</div>
                    </div>
                </div>

                {/* ── Order info grid ─────────────────────────────── */}
                <div style={infoBlock()}>
                    <InfoRow label="Mã đơn hàng:" value={orderNumber} />
                    <InfoRow label="Khách hàng:" value={customerName} />
                    <InfoRow label="Địa chỉ giao hàng:" value={customerAddr} />
                </div>

                {/* ── Items table ─────────────────────────────────── */}
                <table style={itemsTable()}>
                    <thead>
                        <tr>
                            <th style={th({ width: 50 })}>STT</th>
                            <th style={th()}>Tên sản phẩm</th>
                            <th style={th({ width: 80 })}>Số lượng</th>
                            <th style={th({ width: 140 })}>Thành tiền</th>
                        </tr>
                    </thead>
                    <tbody>
                        {items.length === 0 && (
                            <tr>
                                <td colSpan={4} style={td({ textAlign: 'center', fontStyle: 'italic' })}>
                                    Không có sản phẩm
                                </td>
                            </tr>
                        )}
                        {items.map((it, idx) => (
                            <tr key={`${it.productId}-${idx}`}>
                                <td style={td({ textAlign: 'center' })}>{idx + 1}</td>
                                <td style={td()}>
                                    {it.productName || '—'}
                                    {it.sku ? (
                                        <span style={{ color: '#888', marginLeft: 6, fontSize: 12 }}>
                                            (SKU: {it.sku})
                                        </span>
                                    ) : null}
                                </td>
                                <td style={td({ textAlign: 'center' })}>{it.quantity}</td>
                                <td style={td({ textAlign: 'right' })}>{formatCurrency(it.subtotal)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>

                {/* ── Totals ──────────────────────────────────────── */}
                <div style={totalsBlock()}>
                    <div style={totalsRow()}>
                        <div style={totalsLabel()}>Tổng số lượng sản phẩm:</div>
                        <div style={totalsValue()}>{totalQuantity}</div>
                    </div>
                    <div style={totalsRow()}>
                        <div style={totalsLabel()}>Tổng tiền hàng:</div>
                        <div style={{ ...totalsValue(), ...grandTotalStyle() }}>
                            {formatCurrency(total)}
                        </div>
                    </div>
                </div>

                {/* ── Payment method + amount in words ────────────── */}
                <div style={paymentBlock()}>
                    <div style={paymentRow()}>
                        <div style={{ fontWeight: 700 }}>Hình thức thanh toán:</div>
                        <div style={methodChecks()}>
                            <Check label="Tiền mặt" checked={paymentMethod === 'CASH'} />
                            <Check label="Chuyển khoản" checked={paymentMethod === 'BANK_TRANSFER' || paymentMethod === 'SANDBOX_CARD' || paymentMethod === 'SANDBOX_WALLET'} />
                            <Check label="COD" checked={paymentMethod === 'COD'} />
                        </div>
                    </div>
                    <div style={amountWordsRow()}>
                        <span style={{ fontWeight: 700 }}>Số tiền bằng chữ: </span>
                        <span style={{ fontStyle: 'italic' }}>{amountInWords}</span>
                    </div>
                </div>

                {/* ── Signature row: 3 columns ────────────────────── */}
                <div style={signatures()}>
                    <SigBox title="Khách hàng" hint="(Ký và ghi rõ họ tên)" />
                    <SigBox title="NVGH" hint="(Ký và ghi rõ họ tên)" />
                    <SigBox title="Kế toán" hint="(Ký và ghi rõ họ tên)" />
                </div>
            </div>
        </div>
    );
};

// ── Sub-components ────────────────────────────────────────────────────────────

function InfoRow({ label, value }) {
    return (
        <div style={infoRow()}>
            <div style={infoLabel()}>{label}</div>
            <div style={infoValue()}>{value}</div>
        </div>
    );
}

function Check({ label, checked }) {
    return (
        <span style={checkItem()}>
            <span style={checkBox(checked)}>
                {checked && <span style={checkMark()}>✓</span>}
            </span>
            <span>{label}</span>
        </span>
    );
}

function SigBox({ title, hint }) {
    return (
        <div style={sigBox()}>
            <div style={sigTitle()}>{title}</div>
            <div style={sigHint()}>{hint}</div>
            <div style={sigSpace()} />
        </div>
    );
}

// Extract day/month/year from formatted date "dd/mm/yyyy, HH:mm"
function extractDay(s) { return (s || '').split('/')[0] || '……'; }
function extractMonth(s) { return (s || '').split('/')[1] || '……'; }
function extractYear(s) { return (s || '').split('/')[2]?.split(' ')[0] || '……'; }

// ── Vietnamese number-to-words ────────────────────────────────────────────────
function numberToVietnameseText(n) {
    if (!n || n <= 0) return 'Không đồng';
    const units = ['', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
    const scales = ['', 'nghìn', 'triệu', 'tỷ'];

    const intPart = Math.floor(n);
    const round = (v) => Math.round(v * 100) / 100;
    const cents = Math.round((round(n) - intPart) * 100);

    function readThree(num) {
        const h = Math.floor(num / 100);
        const t = Math.floor((num % 100) / 10);
        const u = num % 10;
        let s = '';
        if (h > 0) s += units[h] + ' trăm';
        if (t === 0 && h > 0 && u > 0) s += ' lẻ';
        if (t > 1) s += ' ' + units[t] + ' mươi';
        else if (t === 1) s += ' mười';
        if (u === 1 && t > 1) s += ' mốt';
        else if (u === 5 && t > 1) s += ' lăm';
        else if (u > 0) s += ' ' + units[u];
        return s.trim();
    }

    let words = '';
    let scaleIdx = 0;
    let remaining = intPart;
    while (remaining > 0) {
        const block = remaining % 1000;
        if (block > 0) {
            const blockText = readThree(block);
            words = blockText + (scales[scaleIdx] ? ' ' + scales[scaleIdx] : '') + (words ? ' ' + words : '');
        }
        remaining = Math.floor(remaining / 1000);
        scaleIdx++;
    }

    words = (words || 'không').trim();
    words = words.charAt(0).toUpperCase() + words.slice(1);
    let out = words + ' đồng';
    if (cents > 0) {
        out += ' và ' + readThree(cents) + ' xu';
    }
    return out;
}

export default PaymentReceiptPrint;

// ── Styles ─────────────────────────────────────────────────────────────────────

const NAVY = '#1a3a8a';
// Bank QR image (VietQR). Bundled from /public.
const QR_SRC = '/bank-qr.png';

const page = () => ({
    fontFamily: '"Segoe UI", Arial, sans-serif',
    fontSize: 14,
    color: '#000',
    background: '#fff',
    padding: '16px',
    width: 760,
    boxSizing: 'border-box',
});

const frame = () => ({
    border: `2px solid ${NAVY}`,
    padding: '24px 28px',
});

const topRow = () => ({
    display: 'flex',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 16,
    borderBottom: `2px solid ${NAVY}`,
    paddingBottom: 14,
    marginBottom: 18,
});

const storeName = () => ({
    fontSize: 26,
    fontWeight: 800,
    color: NAVY,
    letterSpacing: 2,
});

const storeSub = () => ({
    fontSize: 13,
    fontStyle: 'italic',
    color: '#555',
    marginTop: 4,
});

const titleCol = () => ({
    flex: 1,
    textAlign: 'center',
});

const title = () => ({
    fontSize: 22,
    fontWeight: 800,
    letterSpacing: 2,
    textTransform: 'uppercase',
});

const titleSub = () => ({
    fontSize: 13,
    fontStyle: 'italic',
    marginTop: 6,
});

const qrCol = () => ({
    width: 140,
    textAlign: 'center',
});

const qrImg = () => ({
    width: 120,
    height: 120,
    border: '1px solid #ddd',
    padding: 4,
    background: '#fff',
    display: 'block',
    margin: '0 auto',
    imageRendering: 'pixelated',
});

const qrCaption = () => ({
    fontSize: 11,
    fontStyle: 'italic',
    color: '#555',
    marginTop: 4,
});

// Info block (mã đơn, tên KH, địa chỉ)
const infoBlock = () => ({
    marginBottom: 16,
});

const infoRow = () => ({
    display: 'grid',
    gridTemplateColumns: '180px 1fr',
    alignItems: 'baseline',
    padding: '4px 0',
    gap: 12,
});

const infoLabel = () => ({
    fontSize: 14,
    fontWeight: 700,
});

const infoValue = () => ({
    fontSize: 14,
    wordBreak: 'break-word',
});

// Items table
const itemsTable = () => ({
    width: '100%',
    borderCollapse: 'collapse',
    marginBottom: 14,
    fontSize: 14,
});

const th = (extra = {}) => ({
    border: '1px solid #333',
    padding: '8px 10px',
    background: '#e8eef9',
    fontSize: 14,
    fontWeight: 700,
    textAlign: 'left',
    ...extra,
});

const td = (extra = {}) => ({
    border: '1px solid #333',
    padding: '8px 10px',
    fontSize: 14,
    verticalAlign: 'top',
    ...extra,
});

// Totals block
const totalsBlock = () => ({
    marginBottom: 16,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-end',
});

const totalsRow = () => ({
    display: 'flex',
    justifyContent: 'space-between',
    width: 360,
    padding: '4px 0',
    fontSize: 14,
});

const totalsLabel = () => ({
    fontWeight: 600,
});

const totalsValue = () => ({
    fontWeight: 700,
    textAlign: 'right',
});

const grandTotalStyle = () => ({
    color: NAVY,
    fontSize: 18,
    fontWeight: 800,
});

// Payment block
const paymentBlock = () => ({
    marginTop: 8,
    marginBottom: 24,
    padding: '12px 14px',
    border: '1px solid #999',
    borderRadius: 4,
});

const paymentRow = () => ({
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    marginBottom: 8,
    flexWrap: 'wrap',
});

const methodChecks = () => ({
    display: 'flex',
    gap: 24,
    flexWrap: 'wrap',
});

const checkItem = () => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    fontSize: 14,
});

const checkBox = (checked) => ({
    width: 16,
    height: 16,
    border: `1.5px solid ${checked ? NAVY : '#333'}`,
    background: '#fff',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
});

const checkMark = () => ({
    color: NAVY,
    fontSize: 12,
    fontWeight: 800,
    lineHeight: 1,
});

const amountWordsRow = () => ({
    fontSize: 14,
    marginTop: 6,
});

// Signature row (3 columns)
const signatures = () => ({
    display: 'flex',
    gap: 24,
    marginTop: 24,
});

const sigBox = () => ({
    flex: 1,
    textAlign: 'center',
});

const sigTitle = () => ({
    fontSize: 14,
    fontWeight: 700,
});

const sigHint = () => ({
    fontSize: 12,
    fontStyle: 'italic',
    color: '#555',
    marginTop: 2,
});

const sigSpace = () => ({
    height: 80,
});