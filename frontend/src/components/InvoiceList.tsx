import { useEffect, useState } from 'react'
import { listInvoices, updateInvoice } from '../api/client'
import type { InvoiceResponse } from '../api/types'
import { formatAmountValue, formatDateTime } from './dateTimeFormat'
import { InvoiceEditableFields } from './InvoiceEditableFields'
import {
  isDraftDirty,
  toDraft,
  toUpdatePayload,
  type InvoiceDraft,
} from './invoiceDraft'

type InvoiceListProps = {
  active: boolean
}

export function InvoiceList({ active }: InvoiceListProps) {
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([])
  const [editingInvoice, setEditingInvoice] = useState<InvoiceResponse | null>(
    null,
  )
  const [editDraft, setEditDraft] = useState<InvoiceDraft | null>(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    if (!active) {
      return
    }

    let cancelled = false
    setIsLoading(true)
    setError('')

    void listInvoices()
      .then((items) => {
        if (!cancelled) {
          setInvoices(items)
        }
      })
      .catch((requestError: unknown) => {
        if (!cancelled) {
          setError(
            requestError instanceof Error
              ? requestError.message
              : 'Unable to load invoices.',
          )
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [active])

  function openEdit(invoice: InvoiceResponse) {
    setEditingInvoice(invoice)
    setEditDraft(toDraft(invoice))
    setError('')
  }

  async function handleSave() {
    if (!editingInvoice || !editDraft || isSaving) {
      return
    }

    setIsSaving(true)
    setError('')
    try {
      const saved = await updateInvoice(
        editingInvoice.id,
        toUpdatePayload(editingInvoice, editDraft),
      )
      setInvoices((current) =>
        current.map((invoice) => (invoice.id === saved.id ? saved : invoice)),
      )
      setEditingInvoice(null)
      setEditDraft(null)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to update invoice.',
      )
    } finally {
      setIsSaving(false)
    }
  }

  if (editingInvoice && editDraft) {
    const editDirty = isDraftDirty(editDraft, editingInvoice)

    return (
      <section className="tool-panel" aria-labelledby="invoice-edit-heading">
        <div className="panel-heading">
          <p className="eyebrow">Document intelligence</p>
          <h2 id="invoice-edit-heading">Edit invoice #{editingInvoice.id}</h2>
          <p>Update invoice details and save them back to the server.</p>
        </div>
        <InvoiceEditableFields
          idPrefix="edit"
          draft={editDraft}
          uploadedDate={editingInvoice.uploadedDate}
          updatedDate={editingInvoice.updatedDate}
          onChange={(field, value) =>
            setEditDraft((current) =>
              current ? { ...current, [field]: value } : current,
            )
          }
        />
        {error && (
          <p className="status-message error" role="alert" aria-label={error}>
            {error}
          </p>
        )}
        <div className="form-actions form-actions-split">
          <button
            className="secondary-button"
            type="button"
            onClick={() => {
              setEditingInvoice(null)
              setEditDraft(null)
            }}
          >
            Cancel
          </button>
          <button
            className="primary-button"
            type="button"
            disabled={!editDirty || isSaving}
            onClick={() => void handleSave()}
          >
            {isSaving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </section>
    )
  }

  return (
    <section
      className="tool-panel"
      aria-labelledby="invoice-list-heading"
      aria-busy={isLoading}
    >
      <div className="panel-heading">
        <p className="eyebrow">Document intelligence</p>
        <h2 id="invoice-list-heading">Uploaded invoices</h2>
        <p>Review every stored invoice and open one to edit.</p>
      </div>
      {error && (
        <p className="status-message error" role="alert" aria-label={error}>
          {error}
        </p>
      )}
      {isLoading && (
        <p className="empty-state" role="status">
          Loading invoices…
        </p>
      )}
      {!isLoading && (
        <div className="invoice-table-wrap">
          <table className="invoice-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Supplier</th>
                <th>Postal code</th>
                <th>Invoice number</th>
                <th>Invoice date</th>
                <th>Amount</th>
                <th>Currency</th>
                <th>Uploaded</th>
                <th>Payment received</th>
                <th>Updated</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr key={invoice.id}>
                  <td>{invoice.id}</td>
                  <td>{invoice.supplier}</td>
                  <td>{invoice.supplierPostalCode}</td>
                  <td>{invoice.invoiceNumber}</td>
                  <td>{invoice.invoiceDate}</td>
                  <td>{formatAmountValue(invoice.amount)}</td>
                  <td>{invoice.currency}</td>
                  <td>{formatDateTime(invoice.uploadedDate)}</td>
                  <td>
                    {invoice.paymentReceivedDate
                      ? formatDateTime(invoice.paymentReceivedDate)
                      : 'Pending'}
                  </td>
                  <td>
                    {invoice.updatedDate
                      ? formatDateTime(invoice.updatedDate)
                      : 'Not updated'}
                  </td>
                  <td>
                    <button
                      className="secondary-button"
                      type="button"
                      onClick={() => openEdit(invoice)}
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {invoices.length === 0 && (
            <p className="empty-state">No invoices have been uploaded yet.</p>
          )}
        </div>
      )}
    </section>
  )
}
