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
      invoiceNumber: 'INV-2026-42',
      amount: 1250.5,
      currency: 'EUR',
    })
    const user = userEvent.setup()
    render(<InvoiceAnalyzer />)
    const file = new File(['invoice'], 'invoice.pdf', {
      type: 'application/pdf',
    })

    await user.upload(screen.getByLabelText('Invoice file'), file)
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Analyze invoice' }))

    expect(analyzeInvoiceMock).toHaveBeenCalledWith(file)
    expect(await screen.findByText('Acme Supplies')).toBeInTheDocument()
    expect(screen.getByText('INV-2026-42')).toBeInTheDocument()
    expect(screen.getByText('EUR 1,250.50')).toBeInTheDocument()
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
