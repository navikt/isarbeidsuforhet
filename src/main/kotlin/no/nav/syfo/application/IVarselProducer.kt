package no.nav.syfo.application

import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IVarselProducer {
    fun sendArbeidstakerForhandsvarsel(
        personIdent: PersonIdent,
        journalpostId: JournalpostId,
        varsel: Varsel
    ): Result<Varsel>
}
