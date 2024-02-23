package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import java.util.*

interface IVurderingRepository {
    // TODO: Replace params with domain object(s)
    fun createForhandsvarsel(
        pdf: ByteArray,
        document: List<DocumentComponent>,
        personIdent: PersonIdent,
        veileder: String,
        type: String,
        begrunnelse: String
    )
}
