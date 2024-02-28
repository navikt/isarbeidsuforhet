package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Varsel
import java.time.OffsetDateTime
import java.util.*

data class PVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val vurderingId: Int,
    val document: List<DocumentComponent>,
    val journalpostId: String?,
    val publishedAt: OffsetDateTime?,
    val expiresAt: OffsetDateTime,
    val expiredPublishedAt: OffsetDateTime?,
) {
    fun toVarsel(): Varsel = Varsel.createFromDatabase(
        uuid = uuid,
        document = document,
        createdAt = createdAt,
        journalpostId = journalpostId,
        publishedAt = publishedAt,
        expiresAt = expiresAt,
        expiredPublishedAt = expiredPublishedAt,
    )
}
