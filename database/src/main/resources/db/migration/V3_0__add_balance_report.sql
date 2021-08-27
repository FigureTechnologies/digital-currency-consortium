CREATE TABLE balance_report(
    uuid UUID NOT NULL PRIMARY KEY,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    completed TIMESTAMPTZ,
    sent TIMESTAMPTZ
);

CREATE TABLE balance_entry(
    uuid UUID NOT NULL PRIMARY KEY,
    balance_report_uuid UUID REFERENCES balance_report(uuid) NOT NULL,
    address TEXT NOT NULL,
    denom TEXT NOT NULL,
    amount TEXT NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX balance_entry_report_idx ON balance_entry(balance_report_uuid);
CREATE UNIQUE INDEX balance_entry_address_report_idx ON balance_entry(balance_report_uuid, address);
