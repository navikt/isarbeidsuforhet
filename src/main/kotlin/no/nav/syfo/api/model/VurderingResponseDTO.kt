package no.nav.syfo.api.model

import no.nav.syfo.domain.*
import no.nav.syfo.domain.Vurdering.AvslagUtenForhandsvarsel.VurderingInitiertAv
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class VurderingResponseDTO private constructor(
    val uuid: UUID,
    val personident: String,
    val createdAt: LocalDateTime,
    val veilederident: String,
    val type: VurderingType,
    val arsak: VurderingArsak?,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val varsel: VarselDTO?,
    val gjelderFom: LocalDate?,
    val vurderingInitiertAv: VurderingInitiertAv?,
    val oppgaveFraNayDato: LocalDate?,
) {
    companion object {
        fun createFromVurdering(vurdering: Vurdering) = VurderingResponseDTO(
            uuid = vurdering.uuid,
            personident = vurdering.personident.value,
            createdAt = vurdering.createdAt.toLocalDateTime(),
            veilederident = vurdering.veilederident,
            type = vurdering.type,
            arsak = vurdering.arsak()?.let { VurderingArsak.valueOf(it) },
            begrunnelse = vurdering.begrunnelse,
            document = vurdering.document,
            varsel = vurdering.varsel?.let { VarselDTO.createFromVarsel(it) },
            gjelderFom = vurdering.gjelderFom,
            vurderingInitiertAv = if (vurdering is Vurdering.AvslagUtenForhandsvarsel) vurdering.vurderingInitiertAv else null,
            oppgaveFraNayDato = vurdering.oppgaveFraNayDato(),
        )
    }
}

data class VarselDTO private constructor(
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val svarfrist: LocalDate,
    val isExpired: Boolean,
) {
    companion object {
        fun createFromVarsel(varsel: Varsel) = VarselDTO(
            uuid = varsel.uuid,
            createdAt = varsel.createdAt.toLocalDateTime(),
            svarfrist = varsel.svarfrist,
            isExpired = varsel.isExpired(),
        )
    }
}
