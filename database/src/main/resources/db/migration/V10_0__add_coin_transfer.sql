CREATE TABLE coin_transfer(
    address_registration_uuid UUID REFERENCES address_registration(uuid),
    address TEXT NOT NULL,
    status TEXT NOT NULL
) INHERITS (tx_request);

CREATE INDEX coin_transfer_status_idx ON coin_transfer(status);
CREATE INDEX coin_transfer_address_registration_uuid_idx ON coin_transfer(address_registration_uuid);

DROP VIEW tx_request_view;

CREATE VIEW tx_request_view AS
SELECT
    uuid,
    'MINT' AS type,
    tx_hash,
    status,
    timeout_height,
    created,
    updated
FROM coin_mint
UNION ALL
SELECT
    uuid,
    'REDEEM_BURN' AS type,
    tx_hash,
    status,
    timeout_height,
    created,
    updated
FROM coin_redeem_burn
UNION ALL
SELECT
    uuid,
    'TRANSFER' AS type,
    tx_hash,
    status,
    timeout_height,
    created,
    updated
FROM coin_transfer
UNION ALL
SELECT
    uuid,
    'TAG' AS type,
    tx_hash,
    status,
    timeout_height,
    created,
    created
FROM address_registration
UNION ALL
SELECT
    uuid,
    'DETAG' AS type,
    tx_hash,
    status,
    timeout_height,
    created,
    created
FROM address_dereg;
