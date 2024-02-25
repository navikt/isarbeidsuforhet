package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class Varsel private constructor(
    val uuid: UUID,
    val document: List<DocumentComponent>,
    val createdAt: OffsetDateTime,
    val journalpostId: String?,
) {
    companion object {
        fun create(document: List<DocumentComponent>) = Varsel(
            uuid = UUID.randomUUID(),
            document = document,
            createdAt = nowUTC(),
            journalpostId = null,
        )
    }
}
