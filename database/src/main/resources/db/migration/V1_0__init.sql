CREATE TABLE event_stream(
    uuid UUID NOT NULL PRIMARY KEY,
    last_block_height BIGINT NOT NULL DEFAULT 1,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    updated TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE TABLE pending_transfer(
    uuid UUID NOT NULL PRIMARY KEY,
    tx_hash TEXT NOT NULL,
    block_height BIGINT NOT NULL,
    amount_with_denom TEXT NOT NULL,
    sender TEXT NOT NULL,
    recipient TEXT NOT NULL,
    status TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);
CREATE INDEX pending_tfr_txhash_idx ON pending_transfer(tx_hash);
CREATE INDEX pending_tfr_status_idx ON pending_transfer(status);

CREATE TABLE tx_status(
    uuid UUID NOT NULL PRIMARY KEY,
    tx_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    raw_log TEXT,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);
CREATE INDEX tx_status_tx_hash_idx ON tx_status(tx_hash);
CREATE INDEX tx_status_status_idx ON tx_status(status);
CREATE INDEX tx_status_created_idx ON tx_status(created);