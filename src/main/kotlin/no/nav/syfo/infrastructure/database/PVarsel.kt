package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.DocumentComponent
import java.time.OffsetDateTime
import java.util.UUID

data class PVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val vurderingId: Int,
    val document: List<DocumentComponent>,
    val journalpostId: String?,
)