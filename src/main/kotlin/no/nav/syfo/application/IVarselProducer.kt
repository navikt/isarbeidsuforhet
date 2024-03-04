package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IVarselProducer {
    fun sendArbeidstakerForhandsvarsel(personIdent: PersonIdent, varsel: Varsel): Result<Varsel>
    fun sendExpiredForhandsvarsel(personIdent: PersonIdent, varsel: Varsel): Result<Varsel>
}
