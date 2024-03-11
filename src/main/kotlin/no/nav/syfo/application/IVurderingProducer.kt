package no.nav.syfo.application

import no.nav.syfo.domain.Vurdering

interface IVurderingProducer {
    fun send(vurdering: Vurdering): Result<Vurdering>
}
