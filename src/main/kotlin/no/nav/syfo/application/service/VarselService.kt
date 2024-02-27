package no.nav.syfo.application.service

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.model.UnpublishedVarsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
) {
    fun publishUnpublishedVarsler(): List<Result<UnpublishedVarsel>> {
        val unpublishedVarsler = varselRepository.getUnpublishedVarsler()

        return unpublishedVarsler.map {
            runCatching {
                varselProducer.sendArbeidstakerVarsel(it)
                varselRepository.setPublished(it)
                it
            }
        }
    }
}
