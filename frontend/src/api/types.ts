export interface ConversationRequest {
  system: string
  question: string
}

export interface ConversationResponse {
  message: string
}

export interface InvoiceUpdateRequest {
  supplier: string
  supplierStreet: string
  supplierStreetNumber: string
  supplierCity: string
  supplierPostalCode: string
  invoiceNumber: string
  invoiceDate: string
  amount: number
  currency: string
  paymentReceivedDate: string | null
}

export interface InvoiceResponse {
  id: number
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
  updatedDate: string | null
}
