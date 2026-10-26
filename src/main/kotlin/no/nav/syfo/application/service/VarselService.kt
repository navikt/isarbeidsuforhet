package no.nav.syfo.application.service

import kotlinx.coroutines.CancellationException
import no.nav.syfo.application.IDokdistClient
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.Varsel

class VarselService(
    private val varselRepository: IVarselRepository,
    private val dokdistClient: IDokdistClient,
) {
    suspend fun publishUnpublishedVarsler(): List<Result<Varsel>> {
        val unpublishedVarsler = varselRepository.getUnpublishedVarsler()
        return unpublishedVarsler.map { (_, journalpostId, varsel) ->
            runCatching {
                dokdistClient.distribuer(journalpostId)
                val publishedVarsel = varsel.publish()
                varselRepository.update(publishedVarsel)
                publishedVarsel
            }.onFailure {
                if (it is CancellationException) throw it
            }
        }
    }
}
