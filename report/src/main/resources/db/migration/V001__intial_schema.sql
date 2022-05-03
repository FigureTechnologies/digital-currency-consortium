CREATE TABLE event_stream(
    uuid UUID NOT NULL PRIMARY KEY,
    last_block_height BIGINT NOT NULL DEFAULT 1,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    updated TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE TABLE coin_movement(
    txid TEXT NOT NULL PRIMARY KEY,
    from_address TEXT NOT NULL,
    to_address TEXT NOT NULL,
    from_member_id TEXT NOT NULL,
    to_member_id TEXT NOT NULL,
    block_height BIGINT NOT NULL,
    block_time TIMESTAMPTZ,
    amount TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);
