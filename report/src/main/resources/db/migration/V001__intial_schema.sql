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
    amount BIGINT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX coin_movement_from_member_id_idx ON coin_movement(from_member_id);
CREATE INDEX coin_movement_to_member_id_idx ON coin_movement(to_member_id);
CREATE INDEX coin_movement_block_height_idx ON coin_movement(block_height);

CREATE TABLE settlement_report(
    uuid UUID NOT NULL PRIMARY KEY,
    from_block_height BIGINT NOT NULL,
    to_block_height BIGINT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE TABLE settlement_net_entry(
    uuid UUID NOT NULL PRIMARY KEY,
    settlement_report_uuid UUID NOT NULL,
    member_id TEXT NOT NULL,
    amount BIGINT NOT NULL
);

ALTER TABLE settlement_net_entry ADD CONSTRAINT set_net_entry_set_report_fk FOREIGN KEY(settlement_report_uuid) REFERENCES settlement_report(uuid);
CREATE UNIQUE INDEX set_net_entry_report_member_uidx ON settlement_net_entry(settlement_report_uuid, member_id);

CREATE TABLE settlement_wire_entry(
    uuid UUID NOT NULL PRIMARY KEY,
    settlement_report_uuid UUID NOT NULL,
    from_member_id TEXT NOT NULL,
    to_member_id TEXT NOT NULL,
    amount BIGINT NOT NULL
);

ALTER TABLE settlement_wire_entry ADD CONSTRAINT set_wire_entry_set_report_fk FOREIGN KEY(settlement_report_uuid) REFERENCES settlement_report(uuid);
CREATE UNIQUE INDEX set_wire_entry_report_from_to_uidx ON settlement_wire_entry(settlement_report_uuid, from_member_id, to_member_id);
