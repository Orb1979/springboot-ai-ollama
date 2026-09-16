import { useEffect, useState, type FormEvent } from 'react'
import {
  listInvoices,
  searchInvoices,
  updateInvoice,
  type InvoiceSearchParams,
} from '../api/client'
import type { InvoiceResponse } from '../api/types'
import { InvoiceDataTable } from './InvoiceDataTable'
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

const emptySearch: InvoiceSearchParams = {
  q: '',
  minAmount: '',
  maxAmount: '',
  currency: '',
  fromDate: '',
  toDate: '',
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
  const [isSearching, setIsSearching] = useState(false)
  const [searchDraft, setSearchDraft] = useState<InvoiceSearchParams>(emptySearch)
  const [activeSearch, setActiveSearch] = useState<InvoiceSearchParams | null>(
    null,
  )

  useEffect(() => {
    if (!active) {
      return
    }

    let cancelled = false
    setIsLoading(true)
    setError('')
    setActiveSearch(null)
    setSearchDraft(emptySearch)

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

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSearching(true)
    setError('')
    try {
      const results = await searchInvoices(searchDraft)
      setInvoices(results)
      setActiveSearch(searchDraft)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to search invoices.',
      )
    } finally {
      setIsSearching(false)
    }
  }

  async function handleClearSearch() {
    setIsSearching(true)
    setError('')
    setSearchDraft(emptySearch)
    setActiveSearch(null)
    try {
      setInvoices(await listInvoices())
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to load invoices.',
      )
    } finally {
      setIsSearching(false)
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

  const resultLabel = activeSearch
    ? `${invoices.length} search result${invoices.length === 1 ? '' : 's'}`
    : undefined

  return (
    <section
      className="tool-panel"
      aria-labelledby="invoice-list-heading"
      aria-busy={isLoading || isSearching}
    >
      <div className="panel-heading">
        <p className="eyebrow">Document intelligence</p>
        <h2 id="invoice-list-heading">Uploaded invoices</h2>
        <p>
          Search by meaning (for example “electrician around €500”) and optionally
          narrow by amount, currency, or date.
        </p>
      </div>
      <form className="invoice-search-form" onSubmit={(event) => void handleSearch(event)}>
        <label className="invoice-search-field" htmlFor="invoice-semantic-search">
          <span>Semantic search</span>
          <input
            id="invoice-semantic-search"
            type="search"
            value={searchDraft.q ?? ''}
            placeholder="e.g. office supplies last quarter"
            onChange={(event) =>
              setSearchDraft((current) => ({ ...current, q: event.target.value }))
            }
          />
        </label>
        <div className="invoice-search-filters">
          <label htmlFor="invoice-min-amount">
            <span>Min amount</span>
            <input
              id="invoice-min-amount"
              inputMode="decimal"
              value={searchDraft.minAmount ?? ''}
              onChange={(event) =>
                setSearchDraft((current) => ({
                  ...current,
                  minAmount: event.target.value,
                }))
              }
            />
          </label>
          <label htmlFor="invoice-max-amount">
            <span>Max amount</span>
            <input
              id="invoice-max-amount"
              inputMode="decimal"
              value={searchDraft.maxAmount ?? ''}
              onChange={(event) =>
                setSearchDraft((current) => ({
                  ...current,
                  maxAmount: event.target.value,
                }))
              }
            />
          </label>
          <label htmlFor="invoice-currency">
            <span>Currency</span>
            <input
              id="invoice-currency"
              value={searchDraft.currency ?? ''}
              placeholder="EUR"
              onChange={(event) =>
                setSearchDraft((current) => ({
                  ...current,
                  currency: event.target.value,
                }))
              }
            />
          </label>
          <label htmlFor="invoice-from-date">
            <span>From date</span>
            <input
              id="invoice-from-date"
              type="date"
              value={searchDraft.fromDate ?? ''}
              onChange={(event) =>
                setSearchDraft((current) => ({
                  ...current,
                  fromDate: event.target.value,
                }))
              }
            />
          </label>
          <label htmlFor="invoice-to-date">
            <span>To date</span>
            <input
              id="invoice-to-date"
              type="date"
              value={searchDraft.toDate ?? ''}
              onChange={(event) =>
                setSearchDraft((current) => ({
                  ...current,
                  toDate: event.target.value,
                }))
              }
            />
          </label>
        </div>
        <div className="form-actions form-actions-split">
          <button
            className="secondary-button"
            type="button"
            disabled={isSearching || (!activeSearch && !searchDraft.q)}
            onClick={() => void handleClearSearch()}
          >
            Show all
          </button>
          <button className="primary-button" type="submit" disabled={isSearching}>
            {isSearching ? 'Searching…' : 'Search'}
          </button>
        </div>
      </form>
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
        <InvoiceDataTable
          invoices={invoices}
          onEdit={openEdit}
          resultLabel={resultLabel}
        />
      )}
    </section>
  )
}
