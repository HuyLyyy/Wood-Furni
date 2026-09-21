import { formatDateTime, formatCurrency } from '../../utils/format.js';

/**
 * PaymentReceiptPrint
 *
 * Vietnamese-style payment receipt (biên nhận thu tiền) modelled on the
 * common "phiếu thu" template:
 *   - Top: Socialist Republic banner
 *   - "WOODFURNI" issuer line
 *   - "BIÊN NHẬN THU TIỀN" heading (bold, underlined)
 *   - Two-column field grid (order code, payer name, phone, address,
 *     reason, amount in figures, amount in words)
 *   - Checkboxes for payment method (Tiền mặt / Chuyển khoản / COD / Kết hợp)
 *   - Signatures: Người nộp tiền + Người thu tiền
 *   - Date placeholders under each signature
 *
 * Everything is inline-styled so the layout survives @media print without
 * external CSS. Designed for A5/A4 portrait.
 */
const PaymentReceiptPrint = ({ order }) => {
    if (!order) return null;

    const total = Number(order.totalAmount || 0);
    const paymentMethod = order.paymentMethod || 'CASH'; // CASH | BANK_TRANSFER | COD | COMBINED
    const orderDate = order.createdAt ? formatDateTime(order.createdAt) : '—';
    const orderNumber = order.orderNumber || order.id || '—';
    const customerName = order.customerName || order.shippingAddress?.label || '—';
    const customerPhone = order.shippingAddress?.phone || '—';
    const customerAddr = [
        order.shippingAddress?.line1,
        order.shippingAddress?.ward,
        order.shippingAddress?.district,
        order.shippingAddress?.city,
    ].filter(Boolean).join(', ') || '—';

    const amountInWords = numberToVietnameseText(total);

    return (
        <div style={page()}>
            <div style={frame()}>
                {/* ── Header: country banner ─────────────────────────── */}
                <div style={countryBanner()}>
                    <div style={countryTitle()}>
                        CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
                    </div>
                    <div style={countrySub()}>
                        Độc lập – Tự do – Hạnh phúc
                    </div>
                    <div style={countryDivider()} />
                </div>

                {/* ── Issuer + receipt title ─────────────────────────── */}
                <div style={titleRow()}>
                    <div style={issuerCol()}>
                        <div style={{ ...issuerLine(), fontWeight: 700 }}>Đơn vị: CÔNG TY WOODFURNI</div>
                        <div style={issuerLine()}>Nội thất gỗ cao cấp</div>
                    </div>
                    <div style={receiptTitleCol()}>
                        <div style={receiptTitle()}>BIÊN NHẬN THU TIỀN</div>
                        <div style={receiptSub()}>Ngày {extractDay(orderDate)} tháng {extractMonth(orderDate)} năm {extractYear(orderDate)}</div>
                    </div>
                </div>

                {/* ── Field grid ─────────────────────────────────────── */}
                <div style={fields()}>
                    <FieldRow label="Mã đơn hàng:" value={orderNumber} />
                    <FieldRow label="Họ tên người nộp:" value={customerName} />
                    <FieldRow label="Số điện thoại:" value={customerPhone} />
                    <FieldRow label="Địa chỉ:" value={customerAddr} multiline />
                    <FieldRow label="Lý do thu:" value="Thanh toán tiền hàng theo đơn hàng" />
                    <FieldRow
                        label="Số tiền:"
                        value={formatCurrency(total)}
                        valueStyle={amountStyle()}
                    />
                    <FieldRow
                        label="Viết bằng chữ:"
                        value={amountInWords}
                        valueStyle={{ fontStyle: 'italic', fontSize: 13 }}
                    />
                </div>

                {/* ── Payment method checkboxes ──────────────────────── */}
                <div style={methodBlock()}>
                    <div style={methodLabel()}>Hình thức thanh toán:</div>
                    <div style={methodRow()}>
                        <Check label="Tiền mặt" checked={paymentMethod === 'CASH'} />
                        <Check label="Chuyển khoản" checked={paymentMethod === 'BANK_TRANSFER'} />
                        <Check label="COD" checked={paymentMethod === 'COD'} />
                        <Check label="Kết hợp" checked={paymentMethod === 'COMBINED'} />
                    </div>
                </div>

                {/* ── Italic note ────────────────────────────────────── */}
                <div style={noteLine()}>
                    (Ghi chú: Biên nhận có giá trị khi có đầy đủ chữ ký của người nộp và người thu tiền)
                </div>

                {/* ── Signatures ─────────────────────────────────────── */}
                <div style={signatures()}>
                    <div style={sigBox()}>
                        <div style={sigTitle()}>Người nộp tiền</div>
                        <div style={sigHint()}>(Ký và ghi rõ họ tên)</div>
                        <div style={sigSpace()} />
                        <div style={dateLine()}>
                            Ngày …… tháng …… năm ……
                        </div>
                    </div>
                    <div style={sigBox()}>
                        <div style={sigTitle()}>Người thu tiền</div>
                        <div style={sigHint()}>(Ký và ghi rõ họ tên)</div>
                        <div style={sigSpace()} />
                        <div style={dateLine()}>
                            Ngày …… tháng …… năm ……
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

// ── Helpers ────────────────────────────────────────────────────────────────────

function FieldRow({ label, value, multiline, valueStyle }) {
    return (
        <div style={fieldRow()}>
            <div style={fieldLabel()}>{label}</div>
            <div
                style={{
                    ...fieldValue(multiline),
                    ...(valueStyle || {}),
                }}
            >
                {value}
            </div>
        </div>
    );
}

function Check({ label, checked }) {
    return (
        <span style={checkItem()}>
            <span style={checkBox(checked)}>
                {checked && <span style={checkMark()}>✓</span>}
            </span>
            <span style={checkLabel()}>{label}</span>
        </span>
    );
}

// Extract day/month/year from formatted date "dd/mm/yyyy, HH:mm"
function extractDay(s) { return (s || '').split('/')[0] || '……'; }
function extractMonth(s) { return (s || '').split('/')[1] || '……'; }
function extractYear(s) { return (s || '').split('/')[2]?.split(' ')[0] || '……'; }

// ── Vietnamese number-to-words (đơn giản, đủ dùng cho biên nhận) ──────────────
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
const NAVY_LIGHT = '#2c52a8';

const page = () => ({
    fontFamily: '"Segoe UI", Arial, sans-serif',
    fontSize: 14,
    color: '#000',
    background: '#fff',
    padding: '16px',
    width: 700,
    boxSizing: 'border-box',
});

const frame = () => ({
    border: `2px solid ${NAVY}`,
    padding: '20px 24px',
});

const countryBanner = () => ({
    textAlign: 'center',
    marginBottom: 12,
});

const countryTitle = () => ({
    fontWeight: 700,
    fontSize: 14,
    letterSpacing: 1,
});

const countrySub = () => ({
    fontStyle: 'italic',
    fontSize: 13,
    fontWeight: 600,
    marginTop: 2,
});

const countryDivider = () => ({
    width: 180,
    height: 1,
    background: '#000',
    margin: '4px auto 0',
});

const titleRow = () => ({
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginTop: 18,
    marginBottom: 16,
});

const issuerCol = () => ({
    flex: 1,
    fontSize: 13,
});

const issuerLine = () => ({
    lineHeight: 1.5,
});

const receiptTitleCol = () => ({
    textAlign: 'center',
    flex: 1,
});

const receiptTitle = () => ({
    fontSize: 22,
    fontWeight: 800,
    letterSpacing: 2,
    textDecoration: 'underline',
});

const receiptSub = () => ({
    fontSize: 13,
    fontStyle: 'italic',
    marginTop: 6,
});

const fields = () => ({
    marginBottom: 16,
});

const fieldRow = () => ({
    display: 'grid',
    gridTemplateColumns: '160px 1fr',
    alignItems: 'baseline',
    padding: '6px 0',
    borderBottom: '1px dashed #888',
    gap: 12,
});

const fieldLabel = () => ({
    fontSize: 14,
    fontWeight: 600,
});

const fieldValue = (multiline) => ({
    fontSize: 14,
    whiteSpace: multiline ? 'normal' : 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
});

const amountStyle = () => ({
    fontSize: 16,
    fontWeight: 800,
    color: NAVY,
});

const methodBlock = () => ({
    marginTop: 18,
    marginBottom: 16,
});

const methodLabel = () => ({
    fontSize: 14,
    fontWeight: 600,
    marginBottom: 8,
});

const methodRow = () => ({
    display: 'flex',
    gap: 28,
    flexWrap: 'wrap',
});

const checkItem = () => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: 8,
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

const checkLabel = () => ({
    fontWeight: 500,
});

const noteLine = () => ({
    fontStyle: 'italic',
    fontSize: 12,
    color: '#555',
    marginTop: 8,
    marginBottom: 24,
    textAlign: 'center',
});

const signatures = () => ({
    display: 'flex',
    gap: 40,
    marginTop: 16,
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
    height: 70,
});

const dateLine = () => ({
    fontSize: 12,
    color: '#444',
    fontStyle: 'italic',
});
