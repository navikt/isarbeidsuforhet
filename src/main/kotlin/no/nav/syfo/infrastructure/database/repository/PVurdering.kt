package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.Vurdering.AvslagUtenForhandsvarsel.VurderingInitiertAv
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PVurdering(
    val id: Int,
    val uuid: UUID,
    val personident: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val veilederident: String,
    val type: String,
    val arsak: String?,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val journalpostId: String?,
    val publishedAt: OffsetDateTime?,
    val gjelderFom: LocalDate?,
    val vurderingInitiertAv: VurderingInitiertAv?,
    val oppgaveFraNayDato: LocalDate?,
) {

    fun toVurdering(
        varsel: Varsel?
    ): Vurdering = Vurdering.createFromDatabase(
        uuid = uuid,
        personident = personident,
        createdAt = createdAt,
        veilederident = veilederident,
        type = type,
        arsak = arsak,
        begrunnelse = begrunnelse,
        document = document,
        journalpostId = journalpostId?.let { JournalpostId(it) },
        varsel = varsel,
        publishedAt = publishedAt,
        gjelderFom = gjelderFom,
        vurderingInitiertAv = vurderingInitiertAv,
        oppgaveFraNayDato = oppgaveFraNayDato,
    )
}
