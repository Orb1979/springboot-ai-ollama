import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { InvoiceResponse } from '../api/types'
import { InvoiceDataTable } from './InvoiceDataTable'

function sampleInvoice(
  overrides: Partial<InvoiceResponse> = {},
): InvoiceResponse {
  return {
    id: 1,
    supplier: 'Acme Supplies',
    supplierStreet: 'Main Street',
    supplierStreetNumber: '42A',
    supplierCity: 'Amsterdam',
    supplierPostalCode: '1012 AB',
    invoiceNumber: 'INV-100',
    invoiceDate: '2024-03-12',
    amount: 99.9,
    currency: 'EUR',
    uploadedDate: '2026-09-15T11:22:22.546024Z',
    paymentReceivedDate: '2026-09-15T11:22:00Z',
    updatedDate: '2026-09-15T11:24:33.512469Z',
    ...overrides,
  }
}

describe('InvoiceDataTable', () => {
  it('filters rows across columns with the search box', async () => {
    const user = userEvent.setup()
    render(
      <InvoiceDataTable
        invoices={[
          sampleInvoice({ id: 1, supplier: 'Acme Supplies' }),
          sampleInvoice({
            id: 2,
            supplier: 'Beta Goods',
            invoiceNumber: 'INV-200',
            amount: 40,
          }),
        ]}
        onEdit={vi.fn()}
      />,
    )

    expect(screen.getByText('Acme Supplies')).toBeInTheDocument()
    expect(screen.getByText('Beta Goods')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search'), 'Beta')

    expect(screen.queryByText('Acme Supplies')).not.toBeInTheDocument()
    expect(screen.getByText('Beta Goods')).toBeInTheDocument()
    expect(screen.getByText('1 of 2 invoices')).toBeInTheDocument()
  })

  it('sorts when a column header is clicked', async () => {
    const user = userEvent.setup()
    render(
      <InvoiceDataTable
        invoices={[
          sampleInvoice({ id: 2, supplier: 'Zebra' }),
          sampleInvoice({ id: 1, supplier: 'Alpha' }),
        ]}
        onEdit={vi.fn()}
      />,
    )

    const supplierHeader = screen.getByRole('columnheader', {
      name: /Supplier/,
    })
    await user.click(within(supplierHeader).getByRole('button'))

    const rows = screen.getAllByRole('row').slice(1)
    expect(rows[0]).toHaveTextContent('Alpha')
    expect(rows[1]).toHaveTextContent('Zebra')

    await user.click(within(supplierHeader).getByRole('button'))
    const descRows = screen.getAllByRole('row').slice(1)
    expect(descRows[0]).toHaveTextContent('Zebra')
    expect(descRows[1]).toHaveTextContent('Alpha')
  })

  it('keeps the current column set without street or city', () => {
    render(
      <InvoiceDataTable invoices={[sampleInvoice()]} onEdit={vi.fn()} />,
    )

    expect(screen.getByRole('columnheader', { name: 'ID' })).toBeInTheDocument()
    expect(screen.getByText('99.90')).toBeInTheDocument()
    expect(screen.getAllByText('2026-09-15 11:22').length).toBeGreaterThan(0)
    expect(screen.queryByRole('columnheader', { name: 'Street' })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: 'City' })).toBeNull()
  })
})
