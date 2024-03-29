package no.nav.syfo.application.service

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.*

class VurderingService(
    private val vurderingRepository: IVurderingRepository,
    private val vurderingPdfService: IVurderingPdfService,
    private val journalforingService: IJournalforingService,
    private val vurderingProducer: IVurderingProducer,
) {
    fun getVurderinger(
        personident: PersonIdent,
    ): List<Vurdering> = vurderingRepository.getVurderinger(personident)

    suspend fun createVurdering(
        personident: PersonIdent,
        veilederident: String,
        type: VurderingType,
        begrunnelse: String,
        document: List<DocumentComponent>,
        callId: String,
    ): Vurdering {
        val vurdering = Vurdering(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            type = type,
        )
        val pdf = if (vurdering.shouldJournalfores()) {
            vurderingPdfService.createVurderingPdf(
                vurdering = vurdering,
                callId = callId,
            )
        } else null

        vurderingRepository.createVurdering(
            vurdering = vurdering,
            pdf = pdf,
        )

        return vurdering
    }

    suspend fun journalforVurderinger(): List<Result<Vurdering>> {
        val notJournalforteVurderinger = vurderingRepository.getNotJournalforteVurderinger()

        return notJournalforteVurderinger.map { (vurdering, pdf) ->
            runCatching {
                val journalpostId = journalforingService.journalfor(
                    personident = vurdering.personident,
                    pdf = pdf,
                    vurdering = vurdering,
                )
                val journalfortVurdering = vurdering.journalfor(
                    journalpostId = JournalpostId(journalpostId.toString()),
                )
                vurderingRepository.update(journalfortVurdering)

                journalfortVurdering
            }
        }
    }

    fun publishUnpublishedVurderinger(): List<Result<Vurdering>> {
        val unpublished = vurderingRepository.getUnpublishedVurderinger()
        return unpublished.map { vurdering ->
            val producerResult = vurderingProducer.send(vurdering = vurdering)
            producerResult.map {
                val publishedVurdering = it.publish()
                vurderingRepository.update(publishedVurdering)
                publishedVurdering
            }
        }
    }
}
