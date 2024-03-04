package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class Vurdering private constructor(
    val uuid: UUID,
    val personident: PersonIdent,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val type: VurderingType,
    val begrunnelse: String,
    val varsel: Varsel?,
) {

    companion object {
        fun createForhandsvarsel(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
        ) = Vurdering(
            uuid = UUID.randomUUID(),
            personident = personident,
            createdAt = nowUTC(),
            veilederident = veilederident,
            type = VurderingType.FORHANDSVARSEL,
            begrunnelse = begrunnelse,
            varsel = Varsel(document),
        )

        fun createFromDatabase(
            uuid: UUID,
            personident: PersonIdent,
            createdAt: OffsetDateTime,
            veilederident: String,
            type: String,
            begrunnelse: String,
            varsel: Varsel?,
        ) = Vurdering(
            uuid = uuid,
            personident = personident,
            createdAt = createdAt,
            veilederident = veilederident,
            type = VurderingType.valueOf(type),
            begrunnelse = begrunnelse,
            varsel = varsel,
        )
    }
}

enum class VurderingType {
    FORHANDSVARSEL, OPPFYLT, STANS
}
