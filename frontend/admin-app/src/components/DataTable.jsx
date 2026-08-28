import PropTypes from 'prop-types';
import './DataTable.css';

/**
 * DataTable — generic table wrapper with consistent header / body / empty
 * state styling. Use for any admin grid (products, orders, inventory).
 *
 * <DataTable
 *   columns={[{ key, header, render?, width?, align? }, ...]}
 *   rows={items}
 *   rowKey="id"
 *   loading={false}
 *   emptyText="Chưa có dữ liệu"
 * />
 */
export default function DataTable({ columns, rows, rowKey = 'id', loading, emptyText = 'Không có dữ liệu', onRowClick }) {
    if (loading) {
        return <div className="data-table__loading">Đang tải...</div>;
    }
    if (!rows || rows.length === 0) {
        return <div className="data-table__empty">{emptyText}</div>;
    }
    return (
        <div className="data-table__wrap">
            <table className="data-table">
                <thead>
                    <tr>
                        {columns.map((col) => (
                            <th
                                key={col.key}
                                style={{ width: col.width, textAlign: col.align || 'left' }}
                            >
                                {col.header}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row) => (
                        <tr
                            key={row[rowKey]}
                            onClick={onRowClick ? () => onRowClick(row) : undefined}
                            style={onRowClick ? { cursor: 'pointer' } : undefined}
                        >
                            {columns.map((col) => (
                                <td key={col.key} style={{ textAlign: col.align || 'left' }}>
                                    {col.render ? col.render(row) : row[col.key]}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

DataTable.propTypes = {
    columns: PropTypes.array.isRequired,
    rows: PropTypes.array,
    rowKey: PropTypes.string,
    loading: PropTypes.bool,
    emptyText: PropTypes.string,
    onRowClick: PropTypes.func,
};