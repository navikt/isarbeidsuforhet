package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering

interface IVarselProducer {
    fun sendArbeidstakerForhandsvarsel(personIdent: PersonIdent, vurdering: Vurdering): Result<Varsel>
    fun sendExpiredForhandsvarsel(personIdent: PersonIdent, varsel: Varsel): Result<Varsel>
}
