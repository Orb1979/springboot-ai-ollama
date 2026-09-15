export interface ConversationRequest {
  system: string
  question: string
}

export interface ConversationResponse {
  message: string
}

export interface InvoiceResponse {
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
}
