DROP TABLE IF EXISTS VARSEL_PDF;
DROP TABLE IF EXISTS VARSEL;
DROP TABLE IF EXISTS VURDERING;

CREATE TABLE VURDERING
(
    id                 SERIAL PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    personident        VARCHAR(11) NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    veilederident      VARCHAR(7)  NOT NULL,
    type               TEXT NOT NULL,
    begrunnelse        TEXT,
    document           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    journalpost_id     VARCHAR(20)
);

CREATE INDEX IX_VURDERING_PERSONIDENT on VURDERING (personident);

CREATE TABLE VARSEL
(
    id                             SERIAL PRIMARY KEY,
    uuid                           CHAR(36)    NOT NULL UNIQUE,
    created_at                     timestamptz NOT NULL,
    updated_at                     timestamptz NOT NULL,
    vurdering_id                   INTEGER     NOT NULL UNIQUE REFERENCES VURDERING (id) ON DELETE CASCADE,
    svarfrist                      DATE NOT NULL,
    published_at                   TIMESTAMPTZ,
    svarfrist_expired_published_at TIMESTAMPTZ
);

CREATE INDEX IX_VARSEL_VURDERING_ID on VARSEL (vurdering_id);

CREATE TABLE VURDERING_PDF
(
    id                       SERIAL PRIMARY KEY,
    uuid                     VARCHAR(50) NOT NULL UNIQUE,
    created_at               timestamptz NOT NULL,
    vurdering_id                INTEGER     NOT NULL UNIQUE REFERENCES VURDERING (id) ON DELETE CASCADE,
    pdf                      bytea       NOT NULL
);

CREATE INDEX IX_VURDERING_PDF_VARSEL_ID on VARSEL_PDF (vurdering_id);
