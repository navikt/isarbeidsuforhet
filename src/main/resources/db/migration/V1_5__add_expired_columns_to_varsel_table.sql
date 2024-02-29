ALTER TABLE varsel
    ADD COLUMN svarfrist TIMESTAMPTZ;

UPDATE varsel
SET svarfrist = created_at + INTERVAL '3 weeks'
WHERE true;

ALTER TABLE varsel
    ALTER COLUMN svarfrist SET NOT NULL;

ALTER TABLE varsel
    ADD COLUMN svarfrist_expired_published_at TIMESTAMPTZ DEFAULT NULL;
