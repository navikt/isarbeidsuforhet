package no.nav.syfo.application.service

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent

class VurderingService(
    private val vurderingRepository: IVurderingRepository,
    private val vurderingPdfService: IVurderingPdfService,
    private val journalforingService: IJournalforingService,
) {
    fun getVurderinger(
        personident: PersonIdent,
    ): List<Vurdering> = vurderingRepository.getVurderinger(personident)

    suspend fun createForhandsvarsel(
        personident: PersonIdent,
        veilederident: String,
        begrunnelse: String,
        document: List<DocumentComponent>,
        callId: String,
    ): Vurdering {
        val pdf = vurderingPdfService.createVurderingPdf(
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

    suspend fun journalforVurderinger(): List<Result<Vurdering>> {
        val notJournalforteVurderinger = vurderingRepository.getNotJournalforteVurderinger()

        return notJournalforteVurderinger.map { (personident, vurdering, pdf) ->
            runCatching {
                val journalpostId = journalforingService.journalfor(
                    personident = personident,
                    pdf = pdf,
                    vurderingUUID = vurdering.uuid,
                )
                val journalfortVurdering = vurdering.journalfor(
                    journalpostId = journalpostId.toString(),
                )
                vurderingRepository.update(journalfortVurdering)

                journalfortVurdering
            }
        }
    }

}
