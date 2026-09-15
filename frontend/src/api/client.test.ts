import { afterEach, describe, expect, it, vi } from 'vitest'
import { analyzeInvoice, listInvoices, sendChat, updateInvoice } from './client'

describe('API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends the system prompt and question as JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: 'Hello!' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      sendChat({ system: 'You are concise.', question: 'Who are you?' }),
    ).resolves.toEqual({ message: 'Hello!' })
    expect(fetchMock).toHaveBeenCalledWith('/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        system: 'You are concise.',
        question: 'Who are you?',
      }),
    })
  })

  it('uploads an invoice using the file multipart field', async () => {
    const invoice = {
      id: 1,
      supplier: 'Acme',
      supplierStreet: 'Main Street',
      supplierStreetNumber: '42A',
      supplierCity: 'Amsterdam',
      supplierPostalCode: '1012 AB',
      invoiceNumber: 'INV-42',
      invoiceDate: '2024-03-12',
      amount: 125.5,
      currency: 'EUR',
      uploadedDate: '2026-09-15T10:30:00Z',
      paymentReceivedDate: null,
      updatedDate: null,
    }
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(invoice), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['invoice'], 'invoice.txt', { type: 'text/plain' })

    await expect(analyzeInvoice(file)).resolves.toMatchObject({
      id: 1,
      supplier: 'Acme',
      invoiceNumber: 'INV-42',
    })
    expect(fetchMock.mock.calls[0][0]).toBe('/ai/invoices/analyze')
  })

  it('lists invoices and updates an invoice by id', async () => {
    const invoice = {
      id: 7,
      supplier: 'Acme',
      supplierStreet: 'Main Street',
      supplierStreetNumber: '42A',
      supplierCity: 'Amsterdam',
      supplierPostalCode: '1012 AB',
      invoiceNumber: 'INV-42',
      invoiceDate: '2024-03-12',
      amount: 125.5,
      currency: 'EUR',
      uploadedDate: '2026-09-15T10:30:00Z',
      paymentReceivedDate: null,
      updatedDate: null,
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify([invoice]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ ...invoice, updatedDate: '2026-09-16T12:00:00Z' }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    await expect(listInvoices()).resolves.toEqual([invoice])
    await expect(updateInvoice(7, invoice)).resolves.toMatchObject({
      id: 7,
      updatedDate: '2026-09-16T12:00:00Z',
    })
    expect(fetchMock.mock.calls[0][0]).toBe('/ai/invoices')
    expect(fetchMock.mock.calls[1][0]).toBe('/ai/invoices/7')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'PUT' })
  })

  it('uses a backend error message when a request fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: 'Invoice could not be read' }), {
          status: 422,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(
      analyzeInvoice(new File(['bad'], 'bad.pdf')),
    ).rejects.toThrow('Invoice could not be read')
  })
})
