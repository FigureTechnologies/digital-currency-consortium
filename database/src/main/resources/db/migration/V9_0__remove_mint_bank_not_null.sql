ALTER TABLE coin_mint ADD COLUMN address TEXT;

UPDATE coin_mint
    SET address = (SELECT address FROM address_registration WHERE address_registration.uuid = coin_mint.address_registration_uuid);

ALTER TABLE coin_mint ALTER COLUMN address SET NOT NULL;
ALTER TABLE coin_mint ALTER COLUMN address_registration_uuid DROP NOT NULL;
