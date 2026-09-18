import type { InvoiceResponse, InvoiceUpdateRequest } from '../api/types'
import {
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from './dateTimeFormat'

export type InvoiceDraft = {
  supplier: string
  supplierStreet: string
  supplierStreetNumber: string
  supplierPostalCode: string
  supplierCity: string
  supplierCountry: string
  invoiceNumber: string
  invoiceDate: string
  amount: string
  currency: string
  paymentReceivedDate: string
}

export function toDraft(invoice: InvoiceResponse): InvoiceDraft {
  return {
    supplier: invoice.supplier,
    supplierStreet: invoice.supplierStreet,
    supplierStreetNumber: invoice.supplierStreetNumber,
    supplierPostalCode: invoice.supplierPostalCode,
    supplierCity: invoice.supplierCity,
    supplierCountry: invoice.supplierCountry,
    invoiceNumber: invoice.invoiceNumber,
    invoiceDate: invoice.invoiceDate,
    amount: String(invoice.amount),
    currency: invoice.currency,
    paymentReceivedDate: toDatetimeLocalValue(invoice.paymentReceivedDate),
  }
}

export function isDraftDirty(
  draft: InvoiceDraft,
  invoice: InvoiceResponse,
): boolean {
  const baseline = toDraft(invoice)
  return (Object.keys(baseline) as Array<keyof InvoiceDraft>).some(
    (key) => draft[key] !== baseline[key],
  )
}

export function toUpdatePayload(
  _invoice: InvoiceResponse,
  draft: InvoiceDraft,
): InvoiceUpdateRequest {
  return {
    supplier: draft.supplier,
    supplierStreet: draft.supplierStreet,
    supplierStreetNumber: draft.supplierStreetNumber,
    supplierPostalCode: draft.supplierPostalCode,
    supplierCity: draft.supplierCity,
    supplierCountry: draft.supplierCountry,
    invoiceNumber: draft.invoiceNumber,
    invoiceDate: draft.invoiceDate,
    amount: Number(draft.amount),
    currency: draft.currency,
    paymentReceivedDate: fromDatetimeLocalValue(draft.paymentReceivedDate),
  }
}
