package no.nav.syfo.application.service

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import java.time.LocalDate

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
        gjelderFom: LocalDate?,
        callId: String,
    ): Vurdering {
        val vurdering = Vurdering(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            type = type,
            gjelderFom = gjelderFom,
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

        when (type) {
            VurderingType.FORHANDSVARSEL -> Metrics.COUNT_VURDERING_FORHANDSVARSEL.increment()
            VurderingType.OPPFYLT -> Metrics.COUNT_VURDERING_OPPFYLT.increment()
            VurderingType.AVSLAG -> Metrics.COUNT_VURDERING_AVSLAG.increment()
        }

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

private class Metrics {
    companion object {
        const val VURDERING_BASE = "${METRICS_NS}_vurdering"

        const val VURDERING_FORHANDSVARSEL = "${VURDERING_BASE}_forhandsvarsel"
        const val VURDERING_OPPFYLT = "${VURDERING_BASE}_oppfylt"
        const val VURDERING_AVSLAG = "${VURDERING_BASE}_avslag"

        val COUNT_VURDERING_FORHANDSVARSEL: Counter = Counter
            .builder(VURDERING_FORHANDSVARSEL)
            .description("Counts the number of successful forhandsvarsel vurderinger")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_OPPFYLT: Counter = Counter
            .builder(VURDERING_OPPFYLT)
            .description("Counts the number of successful oppfylt vurderinger")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_AVSLAG: Counter = Counter
            .builder(VURDERING_AVSLAG)
            .description("Counts the number of successful avslag vurderinger")
            .register(METRICS_REGISTRY)
    }
}
