import type {
  ConversationRequest,
  ConversationResponse,
  InvoiceResponse,
} from './types'

export type InvoiceSearchParams = {
  q?: string
  minAmount?: string
  maxAmount?: string
  currency?: string
  fromDate?: string
  toDate?: string
  limit?: number
}

async function errorMessage(response: Response): Promise<string> {
  const fallback = `Request failed (${response.status})`
  const contentType = response.headers.get('Content-Type') ?? ''

  try {
    if (contentType.includes('application/json')) {
      const body = (await response.json()) as Record<string, unknown>
      const message = body.message ?? body.detail ?? body.error
      return typeof message === 'string' && message.trim() ? message : fallback
    }

    const message = await response.text()
    return message.trim() || fallback
  } catch {
    return fallback
  }
}

async function responseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }

  return response.json() as Promise<T>
}

export async function sendChat(
  request: ConversationRequest,
): Promise<ConversationResponse> {
  const response = await fetch('/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  return responseJson<ConversationResponse>(response)
}

export async function analyzeInvoice(file: File): Promise<InvoiceResponse> {
  const body = new FormData()
  body.append('file', file)

  const response = await fetch('/ai/invoices/analyze', {
    method: 'POST',
    body,
  })

  return responseJson<InvoiceResponse>(response)
}

export async function listInvoices(): Promise<InvoiceResponse[]> {
  const response = await fetch('/ai/invoices')
  return responseJson<InvoiceResponse[]>(response)
}

export async function searchInvoices(
  params: InvoiceSearchParams,
): Promise<InvoiceResponse[]> {
  const query = new URLSearchParams()
  if (params.q?.trim()) {
    query.set('q', params.q.trim())
  }
  if (params.minAmount?.trim()) {
    query.set('minAmount', params.minAmount.trim())
  }
  if (params.maxAmount?.trim()) {
    query.set('maxAmount', params.maxAmount.trim())
  }
  if (params.currency?.trim()) {
    query.set('currency', params.currency.trim())
  }
  if (params.fromDate?.trim()) {
    query.set('fromDate', params.fromDate.trim())
  }
  if (params.toDate?.trim()) {
    query.set('toDate', params.toDate.trim())
  }
  if (params.limit != null) {
    query.set('limit', String(params.limit))
  }

  const suffix = query.toString()
  const response = await fetch(
    suffix ? `/ai/invoices/search?${suffix}` : '/ai/invoices/search',
  )
  return responseJson<InvoiceResponse[]>(response)
}

export async function updateInvoice(
  id: number,
  invoice: InvoiceResponse,
): Promise<InvoiceResponse> {
  const response = await fetch(`/ai/invoices/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(invoice),
  })

  return responseJson<InvoiceResponse>(response)
}
