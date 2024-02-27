package no.nav.syfo.application.service

import no.nav.syfo.application.IVarselPdfService
import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering

class VarselService(
    private val varselRepository: IVarselRepository,
    private val varselProducer: IVarselProducer,
    private val vurderingRepository: IVurderingRepository,
    private val varselPdfService: IVarselPdfService,
) {
    suspend fun createForhandsvarsel(
        personident: PersonIdent,
        veilederident: String,
        begrunnelse: String,
        document: List<DocumentComponent>,
        callId: String,
    ): Vurdering {
        val pdf = varselPdfService.createVarselPdf(
            personident = personident,
            document = document,
            callId = callId,
        )
        val vurdering = Vurdering.createForhandsvarsel(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
        )

        vurderingRepository.createForhandsvarsel(
            pdf = pdf,
            vurdering = vurdering,
        )

        return vurdering
    }

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
}
