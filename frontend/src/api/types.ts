export interface ConversationRequest {
  system: string
  question: string
}

export interface ConversationResponse {
  message: string
}

export interface InvoiceResponse {
  supplier: string
  invoiceNumber: string
  amount: number
  currency: string
}
