package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class Varsel private constructor(
    val uuid: UUID,
    val document: List<DocumentComponent>,
    val createdAt: OffsetDateTime,
    val journalpostId: String?,
    val publishedAt: OffsetDateTime?,
    val expiresAt: OffsetDateTime,
    val expiredPublishedAt: OffsetDateTime?,
) {
    fun publish(): Varsel = this.copy(publishedAt = nowUTC())

    companion object {
        fun create(document: List<DocumentComponent>) = Varsel(
            uuid = UUID.randomUUID(),
            document = document,
            createdAt = nowUTC(),
            journalpostId = null,
            publishedAt = null,
            expiresAt = nowUTC().plusWeeks(3),
            expiredPublishedAt = null,
        )

        fun createFromDatabase(
            uuid: UUID,
            document: List<DocumentComponent>,
            createdAt: OffsetDateTime,
            journalpostId: String?,
            publishedAt: OffsetDateTime?,
            expiresAt: OffsetDateTime,
            expiredPublishedAt: OffsetDateTime?,
        ) = Varsel(
            uuid = uuid,
            document = document,
            createdAt = createdAt,
            journalpostId = journalpostId,
            publishedAt = publishedAt,
            expiresAt = expiresAt,
            expiredPublishedAt = expiredPublishedAt,
        )
    }
}
