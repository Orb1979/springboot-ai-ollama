import type {
  ConversationRequest,
  ConversationResponse,
  InvoiceResponse,
  InvoiceUpdateRequest,
} from './types'

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

export async function updateInvoice(
  id: number,
  invoice: InvoiceUpdateRequest,
): Promise<InvoiceResponse> {
  const response = await fetch(`/ai/invoices/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(invoice),
  })

  return responseJson<InvoiceResponse>(response)
}
