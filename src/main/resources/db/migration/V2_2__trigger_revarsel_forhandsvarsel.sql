UPDATE varsel
SET published_at=null
WHERE uuid in (
    SELECT v.uuid FROM varsel v INNER JOIN vurdering vu ON (v.vurdering_id=vu.id)
    WHERE
        v.published_at >= '2025-03-10T00:00:00' AND v.published_at <= '2025-03-12T10:04:00'
        AND NOT EXISTS (
            SELECT 1 FROM vurdering vu2
            WHERE vu2.created_at > vu.created_at AND vu2.personident = vu.personident
        )
);
