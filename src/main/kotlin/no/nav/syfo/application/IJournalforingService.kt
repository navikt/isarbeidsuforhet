package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Vurdering

interface IJournalforingService {
    suspend fun journalfor(
        personident: PersonIdent,
        pdf: ByteArray,
        vurdering: Vurdering,
    ): Int
}
