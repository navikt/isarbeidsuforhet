package no.nav.syfo.infrastructure.database.repository

import java.time.OffsetDateTime
import java.util.UUID

data class PVurderingPdf(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val vurderingId: Int,
    val pdf: ByteArray,
)
