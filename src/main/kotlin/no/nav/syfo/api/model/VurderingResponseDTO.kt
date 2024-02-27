package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.VurderingType
import java.time.LocalDateTime
import java.util.UUID

data class VurderingResponseDTO private constructor(
    val uuid: UUID,
    val personident: String,
    val createdAt: LocalDateTime,
    val veilederident: String,
    val type: VurderingType,
    val begrunnelse: String,
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
            varsel = if (vurdering.varsel == null) null else VarselDTO.createFromVarsel(vurdering.varsel),
        )
    }
}

data class VarselDTO private constructor(
    val uuid: UUID,
    val document: List<DocumentComponent>,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun createFromVarsel(varsel: Varsel) = VarselDTO(
            uuid = varsel.uuid,
            document = varsel.document,
            createdAt = varsel.createdAt.toLocalDateTime(),
        )
    }
}
