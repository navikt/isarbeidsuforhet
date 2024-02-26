package no.nav.syfo.application

import no.nav.syfo.domain.model.UnpublishedVarsel

interface IVarselProducer {
    fun sendArbeidstakerVarsel(varsel: UnpublishedVarsel)
}
