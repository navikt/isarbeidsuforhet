package no.nav.syfo.application.service

import no.nav.syfo.application.IVarselPdfService
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent

class ForhandsvarselService(
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
}
