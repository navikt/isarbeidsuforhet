package no.nav.syfo.application.service

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.Varsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
) {
    fun publishUnpublishedVarsler(): List<Result<Varsel>> {
        val unpublishedVarsler = varselRepository.getUnpublishedVarsler()
        return unpublishedVarsler.map { (personident, journalpostId, varsel) ->
            val result = varselProducer.sendArbeidstakerForhandsvarsel(
                personIdent = personident,
                journalpostId = journalpostId,
                varsel = varsel,
            )
            result.map {
                val publishedVarsel = varsel.publish()
                varselRepository.update(publishedVarsel)
                publishedVarsel
            }
        }
    }

    fun publishExpiredForhandsvarsler(): List<Result<Varsel>> {
        val expiredUnpublishedVarsler = varselRepository.getUnpublishedExpiredVarsler()
        return expiredUnpublishedVarsler
            .map { (personIdent, expiredUnpublishedVarsel) ->
                val result =
                    varselProducer.sendExpiredForhandsvarsel(personIdent = personIdent, varsel = expiredUnpublishedVarsel)
                result.map {
                    val expiredPublishedVarsel = it.publishSvarfristExpired()
                    varselRepository.update(expiredPublishedVarsel)
                    expiredPublishedVarsel
                }
            }
    }
}
