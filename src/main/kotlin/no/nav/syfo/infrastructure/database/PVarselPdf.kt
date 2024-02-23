package no.nav.syfo.infrastructure.database

import java.time.OffsetDateTime
import java.util.UUID

data class PVarselPdf(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val varselId: Int,
    val pdf: ByteArray,
)
