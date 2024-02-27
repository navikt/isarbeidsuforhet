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
) {
    fun publish(): Varsel = this.copy(publishedAt = nowUTC())

    companion object {
        fun create(document: List<DocumentComponent>) = Varsel(
            uuid = UUID.randomUUID(),
            document = document,
            createdAt = nowUTC(),
            journalpostId = null,
            publishedAt = null,
        )

        fun createFromDatabase(
            uuid: UUID,
            document: List<DocumentComponent>,
            createdAt: OffsetDateTime,
            journalpostId: String?,
            publishedAt: OffsetDateTime?
        ) = Varsel(
            uuid = uuid,
            document = document,
            createdAt = createdAt,
            journalpostId = journalpostId,
            publishedAt = publishedAt,
        )
    }
}
