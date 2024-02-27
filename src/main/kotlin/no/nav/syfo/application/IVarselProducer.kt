package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IVarselProducer {
    fun sendArbeidstakerVarsel(personIdent: PersonIdent, varsel: Varsel)
}
