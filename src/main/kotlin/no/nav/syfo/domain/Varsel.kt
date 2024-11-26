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
) {
    constructor(
        svarfrist: LocalDate,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        publishedAt = null,
        svarfrist = svarfrist,
    )

    fun publish(): Varsel = this.copy(publishedAt = nowUTC())

    fun isExpired(): Boolean = svarfrist.isBefore(LocalDate.now())

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            publishedAt: OffsetDateTime?,
            svarfrist: LocalDate,
        ) = Varsel(
            uuid = uuid,
            createdAt = createdAt,
            publishedAt = publishedAt,
            svarfrist = svarfrist,
        )
    }
}
