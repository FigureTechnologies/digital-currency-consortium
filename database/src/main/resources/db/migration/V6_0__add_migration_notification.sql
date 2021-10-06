CREATE TABLE migration(
    uuid UUID NOT NULL PRIMARY KEY,
    code_id TEXT NOT NULL,
    tx_hash TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    sent TIMESTAMPTZ
);