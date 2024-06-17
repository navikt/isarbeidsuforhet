package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Vurdering

interface IVurderingRepository {
    fun getVurderinger(
        personident: PersonIdent,
    ): List<Vurdering>

    fun getVurderingerBulk(
        personidenter: List<PersonIdent>,
    ): Map<PersonIdent, Vurdering>

    fun getUnpublishedVurderinger(): List<Vurdering>

    fun createVurdering(
        vurdering: Vurdering,
        pdf: ByteArray,
    ): Vurdering

    fun setPublished(vurdering: Vurdering)

    fun setJournalpostId(vurdering: Vurdering)

    fun getNotJournalforteVurderinger(): List<Pair<Vurdering, ByteArray>>
}
