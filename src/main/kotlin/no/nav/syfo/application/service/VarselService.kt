package no.nav.syfo.application.service

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.model.UnpublishedVarsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
) {

    fun getUnpublished(): List<UnpublishedVarsel> {
        return varselRepository.getUnpublishedVarsler()
    }

    fun publish(varsel: UnpublishedVarsel) {
        varselProducer.sendArbeidstakerVarsel(varsel)
        varselRepository.setPublished(varsel)
    }
}
