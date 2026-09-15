import { useMemo, useState } from 'react'
import {
  columnFilteringFeature,
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
}

export function InvoiceDataTable({ invoices, onEdit }: InvoiceDataTableProps) {
  const [globalFilter, setGlobalFilter] = useState('')

  const columns = useMemo(
    () =>
      columnHelper.columns([
        columnHelper.accessor('id', {
          header: 'ID',
          cell: (info) => info.getValue(),
        }),
        columnHelper.accessor('supplier', {
          header: 'Supplier',
          cell: (info) => info.getValue(),
        }),
        columnHelper.accessor('supplierPostalCode', {
          header: 'Postal code',
          cell: (info) => info.getValue(),
        }),
        columnHelper.accessor('invoiceNumber', {
          header: 'Invoice number',
          cell: (info) => info.getValue(),
        }),
        columnHelper.accessor('invoiceDate', {
          header: 'Invoice date',
          cell: (info) => info.getValue(),
        }),
        columnHelper.accessor('amount', {
          header: 'Amount',
          cell: (info) => formatAmountValue(info.getValue()),
        }),
        columnHelper.accessor('currency', {
          header: 'Currency',
          cell: (info) => info.getValue(),
        }),
        columnHelper.accessor(
          (row) => formatDateTime(row.uploadedDate),
          {
            id: 'uploadedDate',
            header: 'Uploaded',
            cell: (info) => info.getValue(),
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
          },
        ),
        columnHelper.accessor(
          (row) =>
            row.updatedDate ? formatDateTime(row.updatedDate) : 'Not updated',
          {
            id: 'updatedDate',
            header: 'Updated',
            cell: (info) => info.getValue(),
          },
        ),
        columnHelper.display({
          id: 'actions',
          header: 'Actions',
          enableSorting: false,
          enableGlobalFilter: false,
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
    globalFilterFn: 'includesString',
    state: {
      globalFilter,
    },
    onGlobalFilterChange: setGlobalFilter,
  })

  const rows = table.getRowModel().rows

  return (
    <div className="invoice-table-panel">
      <div className="invoice-table-toolbar">
        <label className="invoice-search-field" htmlFor="invoice-search">
          <span>Search</span>
          <input
            id="invoice-search"
            type="search"
            value={globalFilter}
            placeholder="Search all columns…"
            onChange={(event) => setGlobalFilter(event.target.value)}
          />
        </label>
        <p className="invoice-result-count" role="status">
          {rows.length === invoices.length
            ? `${invoices.length} invoice${invoices.length === 1 ? '' : 's'}`
            : `${rows.length} of ${invoices.length} invoices`}
        </p>
      </div>
      <div className="invoice-table-wrap">
        <table className="invoice-table">
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
                  <td key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {invoices.length === 0 && (
          <p className="empty-state">No invoices have been uploaded yet.</p>
        )}
        {invoices.length > 0 && rows.length === 0 && (
          <p className="empty-state">No invoices match your search.</p>
        )}
      </div>
    </div>
  )
}
