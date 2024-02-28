package no.nav.syfo.application.service

import no.nav.syfo.application.IExpiredForhandsvarslerProducer
import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.Varsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
    private val expiredForhandsvarslerProducer: IExpiredForhandsvarslerProducer,
) {
    fun publishUnpublishedVarsler(): List<Result<Varsel>> {
        val unpublishedVarsler = varselRepository.getUnpublishedVarsler()
        return unpublishedVarsler.map { (personident, varsel) ->
            runCatching {
                varselProducer.sendArbeidstakerVarsel(personIdent = personident, varsel = varsel)
                val publishedVarsel = varsel.publish()
                varselRepository.update(varsel)
                publishedVarsel
            }
        }
    }

    fun publishExpiredForhandsvarsler(): List<Result<Varsel>> {
        val expiredVarsler = varselRepository.getExpiredVarsler()
        return expiredVarsler.map { (personIdent, expiredVarsel) ->
            expiredForhandsvarslerProducer.send(personIdent, expiredVarsel)
        }
    }
}
