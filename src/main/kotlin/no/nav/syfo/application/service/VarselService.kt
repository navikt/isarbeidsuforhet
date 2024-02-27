package no.nav.syfo.application.service

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.Varsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
    private val journalforingService: IJournalforingService,
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

    suspend fun journalforVarsler(): List<Result<Varsel>> {
        val notJournalforteVarsler = varselRepository.getNotJournalforteVarsler()

        return notJournalforteVarsler.map { (personident, varsel, pdf) ->
            runCatching {
                val journalpostId = journalforingService.journalfor(
                    personident = personident,
                    pdf = pdf,
                    varselUUID = varsel.uuid,
                )
                val journalfortVarsel = varsel.journalfor(
                    journalpostId = journalpostId.toString(),
                )
                varselRepository.update(journalfortVarsel)

                journalfortVarsel
            }
        }
    }
}
