CREATE TABLE event_stream(
    uuid UUID NOT NULL PRIMARY KEY,
    last_block_height BIGINT NOT NULL DEFAULT 1,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    updated TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE TABLE tx_status(
    uuid UUID NOT NULL PRIMARY KEY,
    tx_hash TEXT NOT NULL,
    tx_request_uuid UUID NOT NULL,
    status TEXT NOT NULL,
    type TEXT NOT NULL,
    raw_log TEXT,
    height BIGINT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);
CREATE INDEX tx_status_tx_hash_idx ON tx_status(tx_hash);
CREATE INDEX tx_status_status_idx ON tx_status(status);
CREATE INDEX tx_request_uuid_idx ON tx_status(tx_request_uuid);

CREATE TABLE address_registration(
    uuid UUID NOT NULL PRIMARY KEY,
    bank_account_uuid UUID NOT NULL UNIQUE,
    address TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);
CREATE UNIQUE INDEX address_reg_bank_acct_idx ON address_registration(bank_account_uuid);
CREATE UNIQUE INDEX address_reg_addr_idx ON address_registration(address);

CREATE TABLE tx_request(
    uuid UUID NOT NULL PRIMARY KEY,
    fiat_amount DECIMAL(12, 2) NOT NULL,
    coin_amount BIGINT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    updated TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE TABLE coin_mint(
    address_registration_uuid UUID REFERENCES address_registration(uuid) NOT NULL,
    status TEXT NOT NULL
) INHERITS (tx_request);
CREATE INDEX coin_mint_status_idx ON coin_mint(status);

CREATE TABLE coin_redemption(
    address_registration_uuid UUID REFERENCES address_registration(uuid) NOT NULL,
    status TEXT NOT NULL
) INHERITS (tx_request);
CREATE INDEX coin_redemption_status_idx ON coin_redemption(status);

CREATE TABLE coin_burn(
    coin_redemption_uuid UUID,
    status TEXT NOT NULL
) INHERITS (tx_request);
CREATE INDEX coin_burn_status_idx ON coin_burn(status);

CREATE TABLE marker_transfer(
    from_address TEXT NOT NULL,
    to_address TEXT NOT NULL,
    denom TEXT NOT NULL,
    height BIGINT NOT NULL,
    tx_hash TEXT NOT NULL,
    status TEXT NOT NULL
) INHERITS (tx_request);
CREATE UNIQUE INDEX marker_transfer_txhash_idx ON marker_transfer(tx_hash);