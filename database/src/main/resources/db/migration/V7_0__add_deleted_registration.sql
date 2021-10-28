ALTER TABLE address_registration ADD COLUMN deleted TIMESTAMPTZ;

DROP INDEX address_reg_addr_idx;

CREATE UNIQUE INDEX address_reg_addr_uidx ON address_registration (address, (deleted IS NULL)) WHERE deleted IS NULL;

CREATE TABLE address_dereg(
    uuid UUID NOT NULL PRIMARY KEY,
    address_registration_uuid UUID NOT NULL UNIQUE,
    status TEXT NOT NULL,
    tx_hash TEXT,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

ALTER TABLE address_dereg ADD CONSTRAINT address_dereg_address_reg_fk FOREIGN KEY(address_registration_uuid) REFERENCES address_registration (uuid);
