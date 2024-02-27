package no.nav.syfo.application

import no.nav.syfo.domain.Vurdering

interface IVurderingRepository {
    fun createForhandsvarsel(
        pdf: ByteArray,
        vurdering: Vurdering,
    )
}
