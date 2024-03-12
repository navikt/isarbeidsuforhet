package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.VurderingType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VurderingResponseDTO private constructor(
    val uuid: UUID,
    val personident: String,
    val createdAt: LocalDateTime,
    val veilederident: String,
    val type: VurderingType,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val varsel: VarselDTO?,
) {
    companion object {
        fun createFromVurdering(vurdering: Vurdering) = VurderingResponseDTO(
            uuid = vurdering.uuid,
            personident = vurdering.personident.value,
            createdAt = vurdering.createdAt.toLocalDateTime(),
            veilederident = vurdering.veilederident,
            type = vurdering.type,
            begrunnelse = vurdering.begrunnelse,
            document = vurdering.document,
            varsel = vurdering.varsel?.let { VarselDTO.createFromVarsel(it) },
        )
    }
}

data class VarselDTO private constructor(
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val svarFrist: LocalDate,
) {
    companion object {
        fun createFromVarsel(varsel: Varsel) = VarselDTO(
            uuid = varsel.uuid,
            createdAt = varsel.createdAt.toLocalDateTime(),
            svarFrist = varsel.svarfrist,
        )
    }
}
