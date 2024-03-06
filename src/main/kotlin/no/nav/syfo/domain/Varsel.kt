package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class Varsel private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
    val svarfrist: LocalDate,
    val svarfristExpiredPublishedAt: OffsetDateTime?,
) {
    constructor() : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        publishedAt = null,
        svarfrist = LocalDate.now().plusWeeks(3),
        svarfristExpiredPublishedAt = null,
    )

    fun publish(): Varsel = this.copy(publishedAt = nowUTC())

    fun publishSvarfristExpired(): Varsel = this.copy(svarfristExpiredPublishedAt = nowUTC())

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            publishedAt: OffsetDateTime?,
            svarfrist: LocalDate,
            svarfristExpiredPublishedAt: OffsetDateTime?,
        ) = Varsel(
            uuid = uuid,
            createdAt = createdAt,
            publishedAt = publishedAt,
            svarfrist = svarfrist,
            svarfristExpiredPublishedAt = svarfristExpiredPublishedAt,
        )
    }
}
