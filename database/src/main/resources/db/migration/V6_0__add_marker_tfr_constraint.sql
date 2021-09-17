CREATE UNIQUE INDEX IF NOT EXISTS marker_transfer_txhash_uidx ON marker_transfer(tx_hash);
ALTER TABLE marker_transfer ADD CONSTRAINT unique_tfr_txhash UNIQUE USING INDEX marker_transfer_txhash_uidx;