package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.UUID

data class PVurdering(
    val id: Int,
    val uuid: UUID,
    val personident: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val veilederident: String,
    val type: String,
    val begrunnelse: String,
)
