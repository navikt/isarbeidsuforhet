package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import java.time.OffsetDateTime
import java.util.UUID

data class PVurdering(
    val id: Int,
    val uuid: UUID,
    val personident: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val veilederident: String,
    val type: String,
    val begrunnelse: String,
) {

    fun toVurdering(
        varsel: Varsel?
    ): Vurdering = Vurdering.createFromDatabase(
        uuid = uuid,
        personident = personident,
        createdAt = createdAt,
        veilederident = veilederident,
        type = type,
        begrunnelse = begrunnelse,
        varsel = varsel,
    )
}
