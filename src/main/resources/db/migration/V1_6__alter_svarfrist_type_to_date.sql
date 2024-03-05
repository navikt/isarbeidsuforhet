ALTER TABLE varsel
    ADD COLUMN svarfrist_temp DATE;

UPDATE varsel
SET svarfrist_temp = DATE(svarfrist)
WHERE true;

ALTER TABLE varsel
    DROP COLUMN svarfrist;

ALTER TABLE varsel
    ADD COLUMN svarfrist DATE;

UPDATE varsel
SET svarfrist = DATE(svarfrist_temp)
WHERE true;

ALTER TABLE varsel
    DROP COLUMN svarfrist_temp;
