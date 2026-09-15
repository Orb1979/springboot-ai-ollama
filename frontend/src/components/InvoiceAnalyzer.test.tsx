import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { analyzeInvoice } from '../api/client'
import { InvoiceAnalyzer } from './InvoiceAnalyzer'

vi.mock('../api/client', () => ({
  analyzeInvoice: vi.fn(),
}))

const analyzeInvoiceMock = vi.mocked(analyzeInvoice)

describe('InvoiceAnalyzer', () => {
  beforeEach(() => {
    analyzeInvoiceMock.mockReset()
  })

  it('uploads the selected file and renders the invoice fields', async () => {
    analyzeInvoiceMock.mockResolvedValue({
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
    })
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)
    const file = new File(['invoice'], 'invoice.pdf', {
      type: 'application/pdf',
    })
    expect(screen.getByLabelText('Invoice file')).toHaveAttribute(
      'tabindex',
      '-1',
    )

    await user.upload(screen.getByLabelText('Invoice file'), file)
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))

    expect(analyzeInvoiceMock).toHaveBeenCalledWith(file)
    expect(await screen.findByText('Acme Supplies')).toBeInTheDocument()
    expect(screen.getByText('Main Street 42A')).toBeInTheDocument()
    expect(screen.getByText('1012 AB Amsterdam')).toBeInTheDocument()
    expect(screen.getByText('INV-2026-42')).toBeInTheDocument()
    expect(screen.getByText('2024-03-12')).toBeInTheDocument()
    expect(screen.getByText('EUR 1,250.50')).toBeInTheDocument()
    expect(screen.getByText('2026-09-15T10:30:00Z')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
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

  it('renders the payment received date when present', async () => {
    analyzeInvoiceMock.mockResolvedValue({
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
      paymentReceivedDate: '2026-09-20',
    })
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)

    await user.upload(
      screen.getByLabelText('Invoice file'),
      new File(['invoice'], 'invoice.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))

    expect(await screen.findByText('2026-09-20')).toBeInTheDocument()
    expect(screen.queryByText('Pending')).not.toBeInTheDocument()
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
    expect(
      screen.getByRole('button', { name: 'Analyze invoice' }),
    ).toBeDisabled()
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

  it('ignores an analysis response after a different file is selected', async () => {
    let resolveAnalysis:
      | ((result: {
          supplier: string
          supplierStreet: string
          supplierStreetNumber: string
          supplierCity: string
          supplierPostalCode: string
          invoiceNumber: string
          invoiceDate: string
          amount: number
          currency: string
          uploadedDate: string
          paymentReceivedDate: string | null
        }) => void)
      | undefined
    analyzeInvoiceMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnalysis = resolve
      }),
    )
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)
    const input = screen.getByLabelText('Invoice file')

    await user.upload(
      input,
      new File(['first'], 'first.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))
    await user.upload(
      input,
      new File(['second'], 'second.txt', { type: 'text/plain' }),
    )
    resolveAnalysis?.({
      supplier: 'First Supplier',
      supplierStreet: 'First Street',
      supplierStreetNumber: '1',
      supplierCity: 'First City',
      supplierPostalCode: '1000 AA',
      invoiceNumber: 'FIRST-1',
      invoiceDate: '2024-01-01',
      amount: 10,
      currency: 'EUR',
      uploadedDate: '2026-09-15T10:30:00Z',
      paymentReceivedDate: null,
    })

    expect(await screen.findByText('second.txt')).toBeInTheDocument()
    expect(screen.queryByText('First Supplier')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Analyze invoice' }),
    ).toBeEnabled()
  })
})
