ALTER TABLE invoice
    ADD COLUMN supplier_street VARCHAR(255) NOT NULL,
    ADD COLUMN supplier_street_number VARCHAR(50) NOT NULL,
    ADD COLUMN supplier_city VARCHAR(255) NOT NULL,
    ADD COLUMN supplier_postal_code VARCHAR(50) NOT NULL,
    ADD COLUMN invoice_date DATE NOT NULL,
    ADD COLUMN uploaded_date TIMESTAMPTZ NOT NULL,
    ADD COLUMN payment_received_date DATE;
