CREATE TABLE coin_burn_v2() INHERITS (tx_request);

CREATE INDEX coin_burn_v2_status_idx ON coin_burn_v2(status);

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
    'BURN' AS type,
    tx_hash,
    status,
    timeout_height,
    created,
    updated
FROM coin_burn_v2
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
