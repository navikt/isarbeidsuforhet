ALTER TABLE varsel
    ADD COLUMN expires_at TIMESTAMPTZ;

UPDATE varsel
SET expires_at = created_at + INTERVAL '3 weeks'
WHERE true;

ALTER TABLE varsel
    ALTER COLUMN expires_at SET NOT NULL;

ALTER TABLE varsel
    ADD COLUMN expired_varsel_published_at TIMESTAMPTZ DEFAULT NULL;
