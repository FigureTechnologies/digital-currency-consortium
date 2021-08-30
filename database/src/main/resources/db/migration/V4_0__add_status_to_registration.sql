ALTER TABLE address_registration ADD COLUMN status TEXT;
UPDATE address_registration SET status = 'COMPLETE' WHERE status IS NULL;
ALTER TABLE address_registration ALTER COLUMN status SET NOT NULL;
ALTER TABLE address_registration ADD COLUMN tx_hash TEXT;