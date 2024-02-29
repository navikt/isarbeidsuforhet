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
    val svarfrist: OffsetDateTime,
    val svarfristExpiredPublishedAt: OffsetDateTime?,
) {
    constructor(
        document: List<DocumentComponent>,
        svarfrist: OffsetDateTime = OffsetDateTime.now().plusWeeks(3)
    ) : this(
        uuid = UUID.randomUUID(),
        document = document,
        createdAt = nowUTC(),
        journalpostId = null,
        publishedAt = null,
        svarfrist = svarfrist,
        svarfristExpiredPublishedAt = null,
    )

    fun publish(): Varsel = this.copy(publishedAt = nowUTC())

    fun publishExpiredVarsel(): Varsel = this.copy(svarfristExpiredPublishedAt = nowUTC())

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            document: List<DocumentComponent>,
            createdAt: OffsetDateTime,
            journalpostId: String?,
            publishedAt: OffsetDateTime?,
            svarfrist: OffsetDateTime,
            svarfristExpiredPublishedAt: OffsetDateTime?,
        ) = Varsel(
            uuid = uuid,
            document = document,
            createdAt = createdAt,
            journalpostId = journalpostId,
            publishedAt = publishedAt,
            svarfrist = svarfrist,
            svarfristExpiredPublishedAt = svarfristExpiredPublishedAt,
        )
    }
}
