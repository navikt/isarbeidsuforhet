package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Vurdering

interface IVurderingRepository {
    fun getForhandsvarsel(
        personident: PersonIdent,
    ): List<Vurdering>

    fun createForhandsvarsel(
        pdf: ByteArray,
        vurdering: Vurdering,
    )
}
