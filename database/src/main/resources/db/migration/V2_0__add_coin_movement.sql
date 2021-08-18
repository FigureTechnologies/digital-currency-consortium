CREATE TABLE coin_movement_bookmark(
    uuid UUID NOT NULL PRIMARY KEY,
    last_block_height BIGINT NOT NULL DEFAULT 1,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    updated TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE TABLE coin_movement(
    txid TEXT NOT NULL PRIMARY KEY,
    from_addr TEXT NOT NULL,
    from_addr_bank_uuid UUID,
    to_addr TEXT NOT NULL,
    to_addr_bank_uuid UUID,
    block_height BIGINT NOT NULL,
    block_time TIMESTAMPTZ NOT NULL,
    amount TEXT NOT NULL,
    denom TEXT NOT NULL,
    type TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);
CREATE INDEX coin_movement_block_height_idx ON coin_movement(block_height);
