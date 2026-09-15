import {
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type KeyboardEvent,
} from 'react'
import { analyzeInvoice, updateInvoice } from '../api/client'
import type { InvoiceResponse } from '../api/types'
import { InvoiceEditableFields } from './InvoiceEditableFields'
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

function formatFileSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function InvoiceAnalyzer() {
  const fileInput = useRef<HTMLInputElement>(null)
  const requestId = useRef(0)
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<InvoiceResponse | null>(null)
  const [draft, setDraft] = useState<InvoiceDraft | null>(null)
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

  async function handleUpdate() {
    if (!result || !draft || isSaving) {
      return
    }

    setIsSaving(true)
    setError('')
    try {
      const saved = await updateInvoice(
        result.id,
        toUpdatePayload(result, draft),
      )
      setResult(saved)
      setDraft(toDraft(saved))
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
              onClick={() => void handleUpdate()}
            >
              {isSaving ? 'Updating…' : 'Update'}
            </button>
          </div>
        </section>
      )}
    </section>
  )
}
