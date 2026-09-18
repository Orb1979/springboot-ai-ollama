import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listInvoices, searchInvoices, updateInvoice } from '../api/client'
import type { InvoiceResponse } from '../api/types'
import { InvoiceList } from './InvoiceList'

vi.mock('../api/client', () => ({
  listInvoices: vi.fn(),
  searchInvoices: vi.fn(),
  updateInvoice: vi.fn(),
}))

const listInvoicesMock = vi.mocked(listInvoices)
const searchInvoicesMock = vi.mocked(searchInvoices)
const updateInvoiceMock = vi.mocked(updateInvoice)

function sampleInvoice(
  overrides: Partial<InvoiceResponse> = {},
): InvoiceResponse {
  return {
    id: 42,
    supplier: 'Acme Supplies',
    supplierStreet: 'Main Street',
    supplierStreetNumber: '42A',
    supplierPostalCode: '1012 AB',
    supplierCity: 'Amsterdam',
    supplierCountry: 'Netherlands',
    invoiceNumber: 'INV-2026-42',
    invoiceDate: '2024-03-12',
    amount: 1250.5,
    currency: 'EUR',
    uploadedDate: '2026-09-15T10:30:00Z',
    paymentReceivedDate: null,
    updatedDate: null,
    similarityScore: null,
    ...overrides,
  }
}

describe('InvoiceList', () => {
  beforeEach(() => {
    listInvoicesMock.mockReset()
    searchInvoicesMock.mockReset()
    updateInvoiceMock.mockReset()
  })

  it('loads invoices when active and returns to the list after a successful save', async () => {
    listInvoicesMock.mockResolvedValue([sampleInvoice()])
    updateInvoiceMock.mockResolvedValue(
      sampleInvoice({
        supplierCity: 'Rotterdam',
        updatedDate: '2026-09-17T08:00:00Z',
      }),
    )
    const user = userEvent.setup()
    render(<InvoiceList active />)

    expect(await screen.findByText('Uploaded invoices')).toBeInTheDocument()
    const row = screen.getByRole('row', { name: /Acme Supplies/ })
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    const city = await screen.findByLabelText('City')
    await user.clear(city)
    await user.type(city, 'Rotterdam')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(updateInvoiceMock).toHaveBeenCalledWith(
      42,
      expect.objectContaining({ supplierCity: 'Rotterdam' }),
    )
    expect(
      await screen.findByRole('heading', { name: 'Uploaded invoices' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('1,250.50')).toBeInTheDocument()
    expect(screen.getByText('2026-09-15 10:30')).toBeInTheDocument()
    expect(screen.getByText('2026-09-17 08:00')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Street' })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: 'Number' })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: 'City' })).toBeNull()
  })

  it('runs a semantic search with optional filters', async () => {
    listInvoicesMock.mockResolvedValue([sampleInvoice()])
    searchInvoicesMock.mockResolvedValue([
      sampleInvoice({ id: 9, supplier: 'Electric Works', amount: 480 }),
    ])
    const user = userEvent.setup()
    render(<InvoiceList active />)

    expect(await screen.findByText('Acme Supplies')).toBeInTheDocument()
    await user.type(
      screen.getByLabelText('Semantic search'),
      'electrician around 500',
    )
    await user.type(screen.getByLabelText('Min amount'), '100')
    await user.type(screen.getByLabelText('Currency'), 'EUR')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(searchInvoicesMock).toHaveBeenCalledWith(
      expect.objectContaining({
        q: 'electrician around 500',
        minAmount: '100',
        currency: 'EUR',
      }),
    )
    expect(await screen.findByText('Electric Works')).toBeInTheDocument()
    expect(screen.getByText('1 search result')).toBeInTheDocument()
  })
})
