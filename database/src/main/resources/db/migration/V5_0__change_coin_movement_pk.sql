ALTER TABLE coin_movement ADD COLUMN txid_v2 TEXT;
UPDATE coin_movement SET txid_v2 = (
	SELECT txid FROM coin_movement other
		WHERE coin_movement.txid = other.txid
) || '-0';
ALTER TABLE coin_movement DROP CONSTRAINT coin_movement_pkey;
ALTER TABLE coin_movement ALTER COLUMN txid DROP NOT NULL;
ALTER TABLE coin_movement ALTER COLUMN txid_v2 SET NOT NULL;
ALTER TABLE coin_movement ADD PRIMARY KEY (txid_v2);
