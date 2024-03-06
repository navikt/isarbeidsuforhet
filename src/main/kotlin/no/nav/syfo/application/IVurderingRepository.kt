package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Vurdering

interface IVurderingRepository {
    fun getVurderinger(
        personident: PersonIdent,
    ): List<Vurdering>

    fun createForhandsvarsel(
        pdf: ByteArray,
        vurdering: Vurdering,
    )

    fun update(vurdering: Vurdering)

    fun getNotJournalforteVurderinger(): List<Triple<PersonIdent, Vurdering, ByteArray>>
}
