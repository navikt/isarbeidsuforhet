package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.DocumentComponent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class ExpiredForhandsvarselRecord(
    val personIdent: String,
    val vurderingUuid: UUID,
    val varselUuid: UUID,
    val createdAt: OffsetDateTime,
    val journalpostId: String,
    val svarfrist: LocalDate?,
    val document: List<DocumentComponent>,
)