package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IExpiredForhandsvarselProducer {
    fun send(personIdent: PersonIdent, varsel: Varsel): Result<Varsel>
}
