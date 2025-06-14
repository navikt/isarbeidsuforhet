package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.VurderingArsak
import no.nav.syfo.domain.VurderingType
import java.time.LocalDate

data class VurderingRequestDTO(
    val type: VurderingType,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val gjelderFom: LocalDate? = null,
    val arsak: VurderingArsak? = null,
    val frist: LocalDate? = null,
    val oppgaveFraNayDato: LocalDate? = null,
)
