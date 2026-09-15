import {
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type KeyboardEvent,
} from 'react'
import { analyzeInvoice, listInvoices, updateInvoice } from '../api/client'
import type { InvoiceResponse } from '../api/types'
import {
  InvoiceEditableFields,
} from './InvoiceEditableFields'
import {
  isDraftDirty,
  toDraft,
  toUpdatePayload,
  type InvoiceDraft,
} from './invoiceDraft'

const acceptedFiles =
  '.txt,text/plain,application/pdf,image/png,image/jpeg,image/gif,image/bmp,image/webp'
const supportedMimeTypes = new Set([
  'text/plain',
  'application/pdf',
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/bmp',
  'image/webp',
])
const supportedExtension = /\.(txt|pdf|png|jpe?g|gif|bmp|webp)$/i
const unsupportedFileMessage =
  'Choose a TXT, PDF, PNG, JPEG, GIF, BMP, or WebP file.'

type InvoiceView = 'upload' | 'list' | 'edit'

function formatFileSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatAmount(amount: number, currency: string) {
  return `${currency} ${new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)}`
}

export function InvoiceAnalyzer() {
  const fileInput = useRef<HTMLInputElement>(null)
  const requestId = useRef(0)
  const [view, setView] = useState<InvoiceView>('upload')
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<InvoiceResponse | null>(null)
  const [draft, setDraft] = useState<InvoiceDraft | null>(null)
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([])
  const [editingInvoice, setEditingInvoice] = useState<InvoiceResponse | null>(
    null,
  )
  const [editDraft, setEditDraft] = useState<InvoiceDraft | null>(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isDragging, setIsDragging] = useState(false)

  function selectFile(nextFile: File | undefined) {
    if (!nextFile) {
      return
    }
    requestId.current += 1
    if (
      !supportedMimeTypes.has(nextFile.type) &&
      !supportedExtension.test(nextFile.name)
    ) {
      setFile(null)
      setResult(null)
      setDraft(null)
      setError(unsupportedFileMessage)
      setIsLoading(false)
      if (fileInput.current) {
        fileInput.current.value = ''
      }
      return
    }

    setFile(nextFile)
    setResult(null)
    setDraft(null)
    setError('')
    setIsLoading(false)
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    selectFile(event.target.files?.[0])
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragging(false)
    selectFile(event.dataTransfer.files[0])
  }

  function handleDropZoneKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      fileInput.current?.click()
    }
  }

  async function handleAnalyze() {
    if (!file || isLoading) {
      return
    }

    const activeRequestId = ++requestId.current
    setIsLoading(true)
    setError('')
    setResult(null)
    setDraft(null)

    try {
      const response = await analyzeInvoice(file)
      if (activeRequestId === requestId.current) {
        setResult(response)
        setDraft(toDraft(response))
      }
    } catch (requestError) {
      if (activeRequestId === requestId.current) {
        setError(
          requestError instanceof Error
            ? requestError.message
            : 'Unable to analyze this invoice.',
        )
      }
    } finally {
      if (activeRequestId === requestId.current) {
        setIsLoading(false)
      }
    }
  }

  async function handleShowInvoices() {
    setIsLoading(true)
    setError('')
    try {
      setInvoices(await listInvoices())
      setView('list')
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to load invoices.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  function openEdit(invoice: InvoiceResponse) {
    setEditingInvoice(invoice)
    setEditDraft(toDraft(invoice))
    setError('')
    setView('edit')
  }

  async function saveDraft(
    invoice: InvoiceResponse,
    nextDraft: InvoiceDraft,
    onSaved: (saved: InvoiceResponse) => void,
  ) {
    setIsSaving(true)
    setError('')
    try {
      const saved = await updateInvoice(
        invoice.id,
        toUpdatePayload(invoice, nextDraft),
      )
      onSaved(saved)
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

  const analyzedDirty =
    result !== null && draft !== null ? isDraftDirty(draft, result) : false
  const editDirty =
    editingInvoice !== null && editDraft !== null
      ? isDraftDirty(editDraft, editingInvoice)
      : false

  if (view === 'list') {
    return (
      <section className="tool-panel" aria-labelledby="invoice-list-heading">
        <div className="panel-heading">
          <p className="eyebrow">Document intelligence</p>
          <h2 id="invoice-list-heading">Uploaded invoices</h2>
          <p>Review every stored invoice and open one to edit.</p>
        </div>
        <div className="form-actions form-actions-split">
          <button
            className="secondary-button"
            type="button"
            onClick={() => setView('upload')}
          >
            Back to upload
          </button>
        </div>
        {error && (
          <p className="status-message error" role="alert" aria-label={error}>
            {error}
          </p>
        )}
        <div className="invoice-table-wrap">
          <table className="invoice-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Supplier</th>
                <th>Street</th>
                <th>Number</th>
                <th>City</th>
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
                  <td>{invoice.supplierStreet}</td>
                  <td>{invoice.supplierStreetNumber}</td>
                  <td>{invoice.supplierCity}</td>
                  <td>{invoice.supplierPostalCode}</td>
                  <td>{invoice.invoiceNumber}</td>
                  <td>{invoice.invoiceDate}</td>
                  <td>{formatAmount(invoice.amount, invoice.currency)}</td>
                  <td>{invoice.currency}</td>
                  <td>{invoice.uploadedDate}</td>
                  <td>{invoice.paymentReceivedDate ?? 'Pending'}</td>
                  <td>{invoice.updatedDate ?? 'Not updated'}</td>
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
      </section>
    )
  }

  if (view === 'edit' && editingInvoice && editDraft) {
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
              setView('list')
            }}
          >
            Cancel
          </button>
          <button
            className="primary-button"
            type="button"
            disabled={!editDirty || isSaving}
            onClick={() =>
              void saveDraft(editingInvoice, editDraft, (saved) => {
                setEditingInvoice(saved)
                setEditDraft(toDraft(saved))
                setInvoices((current) =>
                  current.map((invoice) =>
                    invoice.id === saved.id ? saved : invoice,
                  ),
                )
              })
            }
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
      aria-labelledby="invoice-heading"
      aria-busy={isLoading}
    >
      <div className="panel-heading">
        <p className="eyebrow">Document intelligence</p>
        <h2 id="invoice-heading">Analyze an invoice</h2>
        <p>
          Upload an invoice and extract its essential billing details.
        </p>
      </div>

      <div className="form-actions form-actions-split">
        <button
          className="secondary-button"
          type="button"
          disabled={isLoading}
          onClick={() => void handleShowInvoices()}
        >
          Show uploaded invoices
        </button>
      </div>

      <label className="visually-hidden" htmlFor="invoice-file">
        Invoice file
      </label>
      <input
        ref={fileInput}
        id="invoice-file"
        className="visually-hidden"
        type="file"
        tabIndex={-1}
        accept={acceptedFiles}
        onChange={handleFileChange}
      />

      <div
        className={`drop-zone${isDragging ? ' is-dragging' : ''}`}
        role="button"
        tabIndex={0}
        aria-label="Drop invoice file here or choose a file"
        onClick={() => fileInput.current?.click()}
        onKeyDown={handleDropZoneKeyDown}
        onDragEnter={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
      >
        <span className="upload-icon" aria-hidden="true">
          ↑
        </span>
        <strong>Drop your invoice here</strong>
        <span>or choose a file</span>
        <small>TXT, PDF, PNG, JPEG, GIF, BMP, or WebP</small>
      </div>

      {file && (
        <div className="selected-file" aria-live="polite">
          <span className="file-mark" aria-hidden="true">
            DOC
          </span>
          <span>
            <strong>{file.name}</strong>
            <small>
              {file.type || 'Unknown type'} · {formatFileSize(file.size)}
            </small>
          </span>
        </div>
      )}

      <div className="form-actions">
        <button
          className="primary-button"
          type="button"
          disabled={!file || isLoading}
          onClick={() => void handleAnalyze()}
        >
          {isLoading ? 'Analyzing…' : 'Analyze invoice'}
        </button>
        {isLoading && (
          <span className="visually-hidden" role="status">
            Analyzing the selected invoice
          </span>
        )}
      </div>

      {error && (
        <p className="status-message error" role="alert" aria-label={error}>
          {error}
        </p>
      )}

      {result && draft && (
        <section className="response-card" aria-live="polite">
          <p className="eyebrow">Extracted details</p>
          <InvoiceEditableFields
            idPrefix="analyze"
            draft={draft}
            uploadedDate={result.uploadedDate}
            updatedDate={result.updatedDate}
            onChange={(field, value) =>
              setDraft((current) =>
                current ? { ...current, [field]: value } : current,
              )
            }
          />
          <div className="form-actions">
            <button
              className="primary-button"
              type="button"
              disabled={!analyzedDirty || isSaving}
              onClick={() =>
                void saveDraft(result, draft, (saved) => {
                  setResult(saved)
                  setDraft(toDraft(saved))
                })
              }
            >
              {isSaving ? 'Updating…' : 'Update'}
            </button>
          </div>
        </section>
      )}
    </section>
  )
}
