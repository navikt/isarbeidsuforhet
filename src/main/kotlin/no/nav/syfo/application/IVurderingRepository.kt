package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent

interface IVurderingRepository {
    // TODO: Replace params with domain object(s)
    fun createForhandsvarsel(
        pdf: ByteArray,
        document: List<DocumentComponent>,
        personident: PersonIdent,
        veilederident: String,
        begrunnelse: String,
    )
}
