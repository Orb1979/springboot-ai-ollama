import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  analyzeInvoice,
  listInvoices,
  updateInvoice,
} from '../api/client'
import type { InvoiceResponse } from '../api/types'
import { InvoiceAnalyzer } from './InvoiceAnalyzer'

vi.mock('../api/client', () => ({
  analyzeInvoice: vi.fn(),
  listInvoices: vi.fn(),
  updateInvoice: vi.fn(),
}))

const analyzeInvoiceMock = vi.mocked(analyzeInvoice)
const listInvoicesMock = vi.mocked(listInvoices)
const updateInvoiceMock = vi.mocked(updateInvoice)

function sampleInvoice(
  overrides: Partial<InvoiceResponse> = {},
): InvoiceResponse {
  return {
    id: 42,
    supplier: 'Acme Supplies',
    supplierStreet: 'Main Street',
    supplierStreetNumber: '42A',
    supplierCity: 'Amsterdam',
    supplierPostalCode: '1012 AB',
    invoiceNumber: 'INV-2026-42',
    invoiceDate: '2024-03-12',
    amount: 1250.5,
    currency: 'EUR',
    uploadedDate: '2026-09-15T10:30:00Z',
    paymentReceivedDate: null,
    updatedDate: null,
    ...overrides,
  }
}

describe('InvoiceAnalyzer', () => {
  beforeEach(() => {
    analyzeInvoiceMock.mockReset()
    listInvoicesMock.mockReset()
    updateInvoiceMock.mockReset()
  })

  it('uploads the selected file and renders editable invoice fields', async () => {
    analyzeInvoiceMock.mockResolvedValue(sampleInvoice())
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)
    const file = new File(['invoice'], 'invoice.pdf', {
      type: 'application/pdf',
    })

    await user.upload(screen.getByLabelText('Invoice file'), file)
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))

    expect(analyzeInvoiceMock).toHaveBeenCalledWith(file)
    expect(await screen.findByLabelText('Supplier')).toHaveValue(
      'Acme Supplies',
    )
    expect(screen.getByLabelText('Street')).toHaveValue('Main Street')
    expect(screen.getByLabelText('Street number')).toHaveValue('42A')
    expect(screen.getByLabelText('City')).toHaveValue('Amsterdam')
    expect(screen.getByLabelText('Postal code')).toHaveValue('1012 AB')
    expect(screen.getByRole('button', { name: 'Update' })).toBeDisabled()
  })

  it('enables update after edits and saves through the put endpoint', async () => {
    analyzeInvoiceMock.mockResolvedValue(sampleInvoice())
    updateInvoiceMock.mockResolvedValue(
      sampleInvoice({
        supplier: 'Updated Supplies',
        updatedDate: '2026-09-16T11:00:00Z',
      }),
    )
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)

    await user.upload(
      screen.getByLabelText('Invoice file'),
      new File(['invoice'], 'invoice.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))
    const supplier = await screen.findByLabelText('Supplier')
    await user.clear(supplier)
    await user.type(supplier, 'Updated Supplies')

    const updateButton = screen.getByRole('button', { name: 'Update' })
    expect(updateButton).toBeEnabled()
    await user.click(updateButton)

    expect(updateInvoiceMock).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        id: 42,
        supplier: 'Updated Supplies',
        supplierStreet: 'Main Street',
      }),
    )
    expect(await screen.findByText('2026-09-16T11:00:00Z')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Update' })).toBeDisabled()
  })

  it('shows uploaded invoices and opens the edit form', async () => {
    listInvoicesMock.mockResolvedValue([sampleInvoice()])
    updateInvoiceMock.mockResolvedValue(
      sampleInvoice({
        supplierCity: 'Rotterdam',
        updatedDate: '2026-09-17T08:00:00Z',
      }),
    )
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)

    await user.click(
      screen.getByRole('button', { name: 'Show uploaded invoices' }),
    )

    expect(await screen.findByText('Uploaded invoices')).toBeInTheDocument()
    const row = screen.getByRole('row', { name: /Acme Supplies/ })
    expect(within(row).getByText('Main Street')).toBeInTheDocument()
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    const city = await screen.findByLabelText('City')
    await user.clear(city)
    await user.type(city, 'Rotterdam')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(updateInvoiceMock).toHaveBeenCalledWith(
      42,
      expect.objectContaining({ supplierCity: 'Rotterdam' }),
    )
    expect(await screen.findByText('2026-09-17T08:00:00Z')).toBeInTheDocument()
  })

  it('accepts a supported file by drag and drop', () => {
    render(<InvoiceAnalyzer />)
    const file = new File(['invoice'], 'invoice.png', { type: 'image/png' })

    fireEvent.drop(
      screen.getByRole('button', {
        name: 'Drop invoice file here or choose a file',
      }),
      { dataTransfer: { files: [file] } },
    )

    expect(screen.getByText('invoice.png')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Analyze invoice' }),
    ).toBeEnabled()
  })

  it('rejects an unsupported dropped file', () => {
    render(<InvoiceAnalyzer />)

    fireEvent.drop(
      screen.getByRole('button', {
        name: 'Drop invoice file here or choose a file',
      }),
      {
        dataTransfer: {
          files: [
            new File(['program'], 'invoice.exe', {
              type: 'application/octet-stream',
            }),
          ],
        },
      },
    )

    expect(
      screen.getByRole('alert', {
        name: 'Choose a TXT, PDF, PNG, JPEG, GIF, BMP, or WebP file.',
      }),
    ).toBeInTheDocument()
  })

  it('shows an analysis error', async () => {
    analyzeInvoiceMock.mockRejectedValue(new Error('Unsupported invoice'))
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)

    await user.upload(
      screen.getByLabelText('Invoice file'),
      new File(['bad'], 'invoice.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))

    expect(
      await screen.findByRole('alert', { name: 'Unsupported invoice' }),
    ).toBeInTheDocument()
  })
})
