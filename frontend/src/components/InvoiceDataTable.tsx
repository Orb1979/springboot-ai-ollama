import { useMemo } from 'react'
import {
  columnFilteringFeature,
  columnSizingFeature,
  createColumnHelper,
  createFilteredRowModel,
  createSortedRowModel,
  filterFns,
  flexRender,
  globalFilteringFeature,
  rowSortingFeature,
  sortFns,
  tableFeatures,
  useTable,
} from '@tanstack/react-table'
import type { InvoiceResponse } from '../api/types'
import { formatAmountValue, formatDateTime } from './dateTimeFormat'

const features = tableFeatures({
  columnSizingFeature,
  columnFilteringFeature,
  globalFilteringFeature,
  rowSortingFeature,
  filteredRowModel: createFilteredRowModel(),
  sortedRowModel: createSortedRowModel(),
  filterFns,
  sortFns,
})

const columnHelper = createColumnHelper<typeof features, InvoiceResponse>()

type InvoiceDataTableProps = {
  invoices: InvoiceResponse[]
  onEdit: (invoice: InvoiceResponse) => void
  resultLabel?: string
}

function columnWidthStyle(size: number, totalSize: number) {
  return {
    width: `${(size / totalSize) * 100}%`,
  }
}

export function InvoiceDataTable({
  invoices,
  onEdit,
  resultLabel,
}: InvoiceDataTableProps) {
  const columns = useMemo(
    () =>
      columnHelper.columns([
        columnHelper.accessor('id', {
          header: 'ID',
          cell: (info) => info.getValue(),
          size: 30,
          maxSize: 60,
        }),
        columnHelper.accessor('similarityScore', {
          header: 'Score',
          cell: (info) => {
            const score = info.getValue()
            return score == null ? '—' : score.toFixed(3)
          },
          size: 70,
          maxSize: 90,
        }),
        columnHelper.accessor('supplier', {
          header: 'Supplier',
          cell: (info) => info.getValue(),
          size: 120,
        }),
        columnHelper.accessor('invoiceNumber', {
          header: 'Invoice number',
          cell: (info) => info.getValue(),
          size: 110,
        }),
        columnHelper.accessor('invoiceDate', {
          header: 'Invoice date',
          cell: (info) => info.getValue(),
          size: 100,
        }),
        columnHelper.accessor('amount', {
          header: 'Amount',
          cell: (info) => formatAmountValue(info.getValue()),
          size: 80,
        }),
        columnHelper.accessor('currency', {
          header: 'Currency',
          cell: (info) => info.getValue(),
          size: 70,
        }),
        columnHelper.accessor(
          (row) => formatDateTime(row.uploadedDate),
          {
            id: 'uploadedDate',
            header: 'Uploaded',
            cell: (info) => info.getValue(),
            size: 110,
          },
        ),
        columnHelper.accessor(
          (row) =>
            row.paymentReceivedDate
              ? formatDateTime(row.paymentReceivedDate)
              : 'Pending',
          {
            id: 'paymentReceivedDate',
            header: 'Payment received',
            cell: (info) => info.getValue(),
            size: 120,
          },
        ),
        columnHelper.accessor(
          (row) =>
            row.updatedDate ? formatDateTime(row.updatedDate) : 'Not updated',
          {
            id: 'updatedDate',
            header: 'Updated',
            cell: (info) => info.getValue(),
            size: 110,
          },
        ),
        columnHelper.display({
          id: 'actions',
          header: 'Actions',
          enableSorting: false,
          enableGlobalFilter: false,
          size: 70,
          cell: ({ row }) => (
            <button
              className="secondary-button"
              type="button"
              onClick={() => onEdit(row.original)}
            >
              Edit
            </button>
          ),
        }),
      ]),
    [onEdit],
  )

  const table = useTable({
    features,
    columns,
    data: invoices,
    getRowId: (row) => String(row.id),
  })

  const rows = table.getRowModel().rows
  const totalSize = table.getTotalSize()
  const countLabel =
    resultLabel ??
    `${invoices.length} invoice${invoices.length === 1 ? '' : 's'}`

  return (
    <div className="invoice-table-panel">
      <div className="invoice-table-toolbar">
        <p className="invoice-result-count" role="status">
          {countLabel}
        </p>
      </div>
      <div className="invoice-table-wrap">
        <table className="invoice-table">
          <colgroup>
            {table.getAllLeafColumns().map((column) => (
              <col
                key={column.id}
                style={columnWidthStyle(column.getSize(), totalSize)}
              />
            ))}
          </colgroup>
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  const canSort = header.column.getCanSort()
                  const sorted = header.column.getIsSorted()
                  return (
                    <th
                      key={header.id}
                      className={canSort ? 'sortable' : undefined}
                      style={columnWidthStyle(header.getSize(), totalSize)}
                      aria-sort={
                        sorted === 'asc'
                          ? 'ascending'
                          : sorted === 'desc'
                            ? 'descending'
                            : canSort
                              ? 'none'
                              : undefined
                      }
                    >
                      {header.isPlaceholder ? null : canSort ? (
                        <button
                          type="button"
                          className="sort-header-button"
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                          <span className="sort-indicator" aria-hidden="true">
                            {sorted === 'asc'
                              ? ' ↑'
                              : sorted === 'desc'
                                ? ' ↓'
                                : ''}
                          </span>
                        </button>
                      ) : (
                        flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )
                      )}
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                {row.getAllCells().map((cell) => (
                  <td
                    key={cell.id}
                    style={columnWidthStyle(cell.column.getSize(), totalSize)}
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {invoices.length === 0 && (
          <p className="empty-state">No invoices match your search.</p>
        )}
      </div>
    </div>
  )
}
