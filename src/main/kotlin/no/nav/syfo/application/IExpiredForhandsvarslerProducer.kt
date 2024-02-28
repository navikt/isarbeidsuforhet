package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IExpiredForhandsvarslerProducer {
    fun send(personIdent: PersonIdent, varsel: Varsel): Result<Varsel>
}
