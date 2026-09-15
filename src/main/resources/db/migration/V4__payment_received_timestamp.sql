ALTER TABLE invoice
    ALTER COLUMN payment_received_date TYPE TIMESTAMPTZ
        USING CASE
            WHEN payment_received_date IS NULL THEN NULL
            ELSE payment_received_date::timestamp AT TIME ZONE 'UTC'
        END;
