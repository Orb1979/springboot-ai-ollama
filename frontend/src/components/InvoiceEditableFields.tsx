import type { InvoiceDraft } from './invoiceDraft'

type InvoiceEditableFieldsProps = {
  draft: InvoiceDraft
  uploadedDate: string
  updatedDate: string | null
  onChange: (field: keyof InvoiceDraft, value: string) => void
  idPrefix: string
}

export function InvoiceEditableFields({
  draft,
  uploadedDate,
  updatedDate,
  onChange,
  idPrefix,
}: InvoiceEditableFieldsProps) {
  return (
    <div className="invoice-edit-grid">
      <label className="field" htmlFor={`${idPrefix}-supplier`}>
        <span>Supplier</span>
        <input
          id={`${idPrefix}-supplier`}
          value={draft.supplier}
          onChange={(event) => onChange('supplier', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-street`}>
        <span>Street</span>
        <input
          id={`${idPrefix}-street`}
          value={draft.supplierStreet}
          onChange={(event) => onChange('supplierStreet', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-street-number`}>
        <span>Street number</span>
        <input
          id={`${idPrefix}-street-number`}
          value={draft.supplierStreetNumber}
          onChange={(event) =>
            onChange('supplierStreetNumber', event.target.value)
          }
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-city`}>
        <span>City</span>
        <input
          id={`${idPrefix}-city`}
          value={draft.supplierCity}
          onChange={(event) => onChange('supplierCity', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-postal-code`}>
        <span>Postal code</span>
        <input
          id={`${idPrefix}-postal-code`}
          value={draft.supplierPostalCode}
          onChange={(event) =>
            onChange('supplierPostalCode', event.target.value)
          }
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-invoice-number`}>
        <span>Invoice number</span>
        <input
          id={`${idPrefix}-invoice-number`}
          value={draft.invoiceNumber}
          onChange={(event) => onChange('invoiceNumber', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-invoice-date`}>
        <span>Invoice date</span>
        <input
          id={`${idPrefix}-invoice-date`}
          type="date"
          value={draft.invoiceDate}
          onChange={(event) => onChange('invoiceDate', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-amount`}>
        <span>Amount</span>
        <input
          id={`${idPrefix}-amount`}
          type="number"
          step="0.01"
          value={draft.amount}
          onChange={(event) => onChange('amount', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-currency`}>
        <span>Currency</span>
        <input
          id={`${idPrefix}-currency`}
          value={draft.currency}
          onChange={(event) => onChange('currency', event.target.value)}
        />
      </label>
      <label className="field" htmlFor={`${idPrefix}-payment-date`}>
        <span>Payment received date</span>
        <input
          id={`${idPrefix}-payment-date`}
          type="date"
          value={draft.paymentReceivedDate}
          onChange={(event) =>
            onChange('paymentReceivedDate', event.target.value)
          }
        />
      </label>
      <div className="field readonly-field">
        <span>Uploaded</span>
        <p>
          <time dateTime={uploadedDate}>{uploadedDate}</time>
        </p>
      </div>
      <div className="field readonly-field">
        <span>Updated</span>
        <p>
          {updatedDate ? (
            <time dateTime={updatedDate}>{updatedDate}</time>
          ) : (
            'Not updated'
          )}
        </p>
      </div>
    </div>
  )
}
