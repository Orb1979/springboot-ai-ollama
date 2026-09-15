import { afterEach, describe, expect, it, vi } from 'vitest'
import { analyzeInvoice, sendChat } from './client'

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
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
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
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['invoice'], 'invoice.txt', { type: 'text/plain' })

    await expect(analyzeInvoice(file)).resolves.toMatchObject({
      supplier: 'Acme',
      supplierStreet: 'Main Street',
      supplierStreetNumber: '42A',
      supplierCity: 'Amsterdam',
      supplierPostalCode: '1012 AB',
      invoiceNumber: 'INV-42',
      invoiceDate: '2024-03-12',
      uploadedDate: '2026-09-15T10:30:00Z',
      paymentReceivedDate: null,
    })
    const [, request] = fetchMock.mock.calls[0]
    expect(fetchMock.mock.calls[0][0]).toBe('/ai/invoices/analyze')
    expect(request.method).toBe('POST')
    expect(request.body).toBeInstanceOf(FormData)
    expect(request.body.get('file')).toBe(file)
    expect(request.headers).toBeUndefined()
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
