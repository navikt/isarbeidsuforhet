package no.nav.syfo.application.service

import no.nav.syfo.application.IExpiredForhandsvarselProducer
import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.Varsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
    private val expiredForhandsvarselProducer: IExpiredForhandsvarselProducer,
) {
    fun publishUnpublishedVarsler(): List<Result<Varsel>> {
        val unpublishedVarsler = varselRepository.getUnpublishedVarsler()
        return unpublishedVarsler.map { (personident, varsel) ->
            runCatching {
                varselProducer.sendArbeidstakerForhandsvarsel(personIdent = personident, varsel = varsel)
                val publishedVarsel = varsel.publish()
                varselRepository.update(publishedVarsel)
                publishedVarsel
            }
        }
    }

    fun publishExpiredForhandsvarsler(): List<Result<Varsel>> {
        val expiredUnpublishedVarsler = varselRepository.getUnpublishedExpiredVarsler()
        return expiredUnpublishedVarsler.map { (personIdent, expiredUnpublishedVarsel) ->
            runCatching {
                expiredForhandsvarselProducer.send(
                    personIdent = personIdent, varsel = expiredUnpublishedVarsel
                )
                val expiredPublishedVarsel = expiredUnpublishedVarsel.publishExpiredVarsel()
                varselRepository.update(expiredPublishedVarsel)
                expiredPublishedVarsel
            }
        }
    }
}
