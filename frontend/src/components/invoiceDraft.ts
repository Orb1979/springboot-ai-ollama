import type { InvoiceResponse } from '../api/types'
import {
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from './dateTimeFormat'

export type InvoiceDraft = {
  supplier: string
  supplierStreet: string
  supplierStreetNumber: string
  supplierCity: string
  supplierPostalCode: string
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
    supplierCity: invoice.supplierCity,
    supplierPostalCode: invoice.supplierPostalCode,
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
  invoice: InvoiceResponse,
  draft: InvoiceDraft,
): InvoiceResponse {
  return {
    ...invoice,
    supplier: draft.supplier,
    supplierStreet: draft.supplierStreet,
    supplierStreetNumber: draft.supplierStreetNumber,
    supplierCity: draft.supplierCity,
    supplierPostalCode: draft.supplierPostalCode,
    invoiceNumber: draft.invoiceNumber,
    invoiceDate: draft.invoiceDate,
    amount: Number(draft.amount),
    currency: draft.currency,
    paymentReceivedDate: fromDatetimeLocalValue(draft.paymentReceivedDate),
  }
}
