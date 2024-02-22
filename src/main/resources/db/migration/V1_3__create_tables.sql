CREATE TABLE VURDERING
(
    id                 SERIAL PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    personident        VARCHAR(11) NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    veilederident      VARCHAR(7)  NOT NULL,
    type               VARCHAR(20) NOT NULL,
    begrunnelse        TEXT
);

CREATE INDEX IX_VURDERING_PERSONIDENT on VURDERING (personident);

CREATE TABLE VARSEL
(
    id                          SERIAL PRIMARY KEY,
    uuid                        CHAR(36)    NOT NULL UNIQUE,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    vurdering_id                INTEGER     NOT NULL UNIQUE REFERENCES VURDERING (id) ON DELETE CASCADE,
    document                    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    journalpost_id              VARCHAR(20)
);

CREATE INDEX IX_VARSEL_VURDERING_ID on VARSEL (vurdering_id);

CREATE TABLE VARSEL_PDF
(
    id                       SERIAL PRIMARY KEY,
    uuid                     VARCHAR(50) NOT NULL UNIQUE,
    created_at               timestamptz NOT NULL,
    varsel_id                INTEGER     NOT NULL UNIQUE REFERENCES VARSEL (id) ON DELETE CASCADE,
    pdf                      bytea       NOT NULL
);

CREATE INDEX IX_VARSEL_PDF_VARSEL_ID on VARSEL_PDF (varsel_id);
