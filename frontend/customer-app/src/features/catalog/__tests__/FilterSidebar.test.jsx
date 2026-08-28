/**
 * TC-FE-FILTER-01 — FilterSidebar: thay đổi filter gọi onChange callback
 * với payload đúng shape, mapping UI field → API param.
 *
 * Mục tiêu: bảo đảm contract giữa URL state (filters) ↔ component ↔
 * API params được giữ vững khi refactor. Khi user chọn "LIVING_ROOM" ở
 * dropdown, `onChange({ room: 'LIVING_ROOM' })` phải được gọi đúng 1 lần —
 * không có key thừa, không có null khi user chọn option thật.
 */
import { describe, it, expect, vi } from 'vitest';
import { useState } from 'react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../../test/render.jsx';
import FilterSidebar from '../FilterSidebar.jsx';
import { ENVIRONMENTS, ROOMS, WOOD_TYPES } from '../../../utils/catalogMeta.js';

const CATEGORIES = [
    { id: 'c1', slug: 'sofa', name: 'Sofa' },
    { id: 'c2', slug: 'ban-tra', name: 'Bàn trà' },
];

function renderSidebar(initialFilters = {}) {
    const onChange = vi.fn();
    const onReset = vi.fn();
    // Wrap the sidebar in a parent that holds `filters` in state so each
    // onChange patch causes a fresh prop reference (and therefore a
    // re-render). FilterSidebar reads its values from `filters` — without
    // this re-render, controlled inputs would keep reverting to the
    // empty initial value and subsequent keystrokes would be lost.
    function Host() {
        const [filters, setFilters] = useState(initialFilters);
        return (
            <FilterSidebar
                filters={filters}
                categories={CATEGORIES}
                onChange={(patch) => {
                    onChange(patch);
                    setFilters((prev) => ({ ...prev, ...patch }));
                }}
                onReset={() => {
                    onReset();
                    setFilters({});
                }}
            />
        );
    }
    const utils = renderWithProviders(<Host />);
    return { ...utils, onChange, onReset };
}

describe('FilterSidebar', () => {
    it('TC-FE-FILTER-01a: chọn category → onChange({ category: <slug> })', async () => {
        const user = userEvent.setup();
        const { onChange } = renderSidebar({});
        await user.selectOptions(
            document.getElementById('flt-category'),
            'ban-tra'
        );
        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenCalledWith({ category: 'ban-tra' });
    });

    it('TC-FE-FILTER-01b: chọn environment INDOOR → onChange({ environment: "INDOOR" })', async () => {
        const user = userEvent.setup();
        const { onChange } = renderSidebar({});
        await user.selectOptions(
            document.getElementById('flt-environment'),
            ENVIRONMENTS[0].value // INDOOR
        );
        expect(onChange).toHaveBeenCalledWith({ environment: 'INDOOR' });
    });

    it('TC-FE-FILTER-01c: chọn room LIVING_ROOM → onChange({ room: "LIVING_ROOM" })', async () => {
        const user = userEvent.setup();
        const { onChange } = renderSidebar({});
        await user.selectOptions(
            document.getElementById('flt-room'),
            ROOMS[0].value // LIVING_ROOM
        );
        expect(onChange).toHaveBeenCalledWith({ room: 'LIVING_ROOM' });
    });

    it('TC-FE-FILTER-01d: chọn woodType OAK → onChange({ woodType: "OAK" })', async () => {
        const user = userEvent.setup();
        const { onChange } = renderSidebar({});
        await user.selectOptions(
            document.getElementById('flt-woodType'),
            WOOD_TYPES[0].value // OAK
        );
        expect(onChange).toHaveBeenCalledWith({ woodType: 'OAK' });
    });

    it('TC-FE-FILTER-01e: nhập keyword → onChange({ keyword: "<text>" })', async () => {
        const user = userEvent.setup();
        const { onChange } = renderSidebar({});
        const input = document.getElementById('flt-keyword');
        await user.type(input, 'sofa');
        // 4 keystrokes → 4 onChange. Last call carries the full string.
        expect(onChange).toHaveBeenCalled();
        expect(onChange.mock.calls.at(-1)[0]).toEqual({ keyword: 'sofa' });
    });

    it('TC-FE-FILTER-01f: nhập minPrice/maxPrice → 2 onChange riêng biệt', async () => {
        const user = userEvent.setup();
        const { onChange } = renderSidebar({});
        await user.type(document.getElementById('flt-minPrice'), '100000');
        await user.type(document.getElementById('flt-maxPrice'), '500000');

        const allCalls = onChange.mock.calls.map((c) => c[0]);
        const lastMin = [...allCalls].reverse().find((c) => 'minPrice' in c);
        const lastMax = [...allCalls].reverse().find((c) => 'maxPrice' in c);
        expect(lastMin).toEqual({ minPrice: '100000' });
        expect(lastMax).toEqual({ maxPrice: '500000' });
    });

    it('TC-FE-FILTER-01g: chọn "Tất cả" (empty) → onChange({ category: null })', async () => {
        // Đảm bảo URL state được clear khi user reset selection 1 field.
        const user = userEvent.setup();
        const { onChange } = renderSidebar({ category: 'sofa' });
        await user.selectOptions(document.getElementById('flt-category'), '');
        expect(onChange).toHaveBeenCalledWith({ category: null });
    });

    it('TC-FE-FILTER-01h: bấm "Xoá hết" → onReset() gọi 1 lần, không gọi onChange', async () => {
        const user = userEvent.setup();
        const { onChange, onReset } = renderSidebar({ category: 'sofa' });
        await user.click(document.getElementById('flt-keyword')); // focus first
        // Reset button uses CSS class, not id. Query by text.
        const resetBtn = [...document.querySelectorAll('button')].find(
            (b) => b.textContent.trim() === 'Xoá hết'
        );
        await user.click(resetBtn);
        expect(onReset).toHaveBeenCalledTimes(1);
        expect(onChange).not.toHaveBeenCalled();
    });
});
