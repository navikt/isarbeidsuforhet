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
}
