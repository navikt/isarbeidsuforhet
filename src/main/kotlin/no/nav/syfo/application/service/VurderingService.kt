package no.nav.syfo.application.service

import io.micrometer.core.instrument.Counter
import no.nav.syfo.domain.VurderingArsak
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

    fun getLatestVurderingForPersoner(
        personidenter: List<PersonIdent>,
    ): Map<PersonIdent, Vurdering> = vurderingRepository.getLatestVurderingForPersoner(personidenter)

    suspend fun createVurdering(
        personident: PersonIdent,
        veilederident: String,
        type: VurderingType,
        arsak: VurderingArsak?,
        begrunnelse: String,
        document: List<DocumentComponent>,
        gjelderFom: LocalDate?,
        svarfrist: LocalDate? = null,
        nayOppgaveDato: LocalDate? = null,
        callId: String,
    ): Vurdering {
        val currentVurdering = getVurderinger(personident).firstOrNull()
        if (type == VurderingType.FORHANDSVARSEL && currentVurdering is Vurdering.Forhandsvarsel) {
            throw IllegalArgumentException("Duplicate ${VurderingType.FORHANDSVARSEL} for given person")
        }
        if (type == VurderingType.FORHANDSVARSEL && svarfrist == null) {
            throw IllegalArgumentException("${VurderingType.FORHANDSVARSEL} requires frist")
        }
        if (type == VurderingType.AVSLAG && (currentVurdering == null || !currentVurdering.isExpiredForhandsvarsel())) {
            throw IllegalArgumentException("Cannot create ${VurderingType.AVSLAG} without expired ${VurderingType.FORHANDSVARSEL}")
        }
        if (listOf(VurderingType.AVSLAG_UTEN_FORHANDSVARSEL, VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL, VurderingType.IKKE_AKTUELL).contains(type) && arsak == null) {
            throw IllegalArgumentException("$type requires arsak")
        }

        val vurdering = when (type) {
            VurderingType.FORHANDSVARSEL -> Vurdering.Forhandsvarsel(
                personident = personident,
                veilederident = veilederident,
                begrunnelse = begrunnelse,
                document = document,
                svarfrist = svarfrist!!,
            )
            VurderingType.OPPFYLT -> Vurdering.Oppfylt(
                personident = personident,
                veilederident = veilederident,
                begrunnelse = begrunnelse,
                document = document,
            )
            VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL -> Vurdering.OppfyltUtenForhandsvarsel(
                personident = personident,
                veilederident = veilederident,
                arsak = Vurdering.OppfyltUtenForhandsvarsel.Arsak.valueOf(arsak!!.name),
                begrunnelse = begrunnelse,
                document = document,
                oppgaveFraNayDato = nayOppgaveDato,
            )
            VurderingType.AVSLAG -> Vurdering.Avslag(
                personident = personident,
                veilederident = veilederident,
                begrunnelse = begrunnelse,
                document = document,
                gjelderFom = gjelderFom ?: throw IllegalArgumentException("gjelderFom is required for $type")
            )
            VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> Vurdering.AvslagUtenForhandsvarsel(
                personident = personident,
                veilederident = veilederident,
                arsak = Vurdering.AvslagUtenForhandsvarsel.Arsak.valueOf(arsak!!.name),
                begrunnelse = begrunnelse,
                document = document,
                gjelderFom = gjelderFom ?: throw IllegalArgumentException("gjelderFom is required for $type"),
                oppgaveFraNayDato = nayOppgaveDato,
            )
            VurderingType.IKKE_AKTUELL -> Vurdering.IkkeAktuell(
                personident = personident,
                veilederident = veilederident,
                arsak = Vurdering.IkkeAktuell.Arsak.valueOf(arsak!!.name),
                document = document,
            )
        }

        val pdf = vurderingPdfService.createVurderingPdf(
            vurdering = vurdering,
            callId = callId,
        )

        vurderingRepository.createVurdering(
            vurdering = vurdering,
            pdf = pdf,
        )

        when (type) {
            VurderingType.FORHANDSVARSEL -> Metrics.COUNT_VURDERING_FORHANDSVARSEL.increment()
            VurderingType.OPPFYLT -> Metrics.COUNT_VURDERING_OPPFYLT.increment()
            VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL -> Metrics.COUNT_VURDERING_UTEN_FORHANDSVARSEL_OPPFYLT.increment()
            VurderingType.AVSLAG -> Metrics.COUNT_VURDERING_AVSLAG.increment()
            VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> Metrics.COUNT_VURDERING_AVSLAG_UTEN_FORHANDSVARSEL.increment()
            VurderingType.IKKE_AKTUELL -> Metrics.COUNT_VURDERING_IKKE_AKTUELL.increment()
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
                vurderingRepository.setJournalpostId(journalfortVurdering)

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
                vurderingRepository.setPublished(publishedVurdering)
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
        const val VURDERING_OPPFYLT_UTEN_FORHANDSVARSEL = "${VURDERING_BASE}_oppfylt_uten_forhandsvarsel"
        const val VURDERING_AVSLAG = "${VURDERING_BASE}_avslag"
        const val VURDERING_AVSLAG_UTEN_FORHANDSVARSEL = "${VURDERING_BASE}_avslag_uten_forhandsvarsel"
        const val VURDERING_IKKE_AKTUELL = "${VURDERING_BASE}_ikke_aktuell"

        val COUNT_VURDERING_FORHANDSVARSEL: Counter = Counter
            .builder(VURDERING_FORHANDSVARSEL)
            .description("Counts the number of successful forhandsvarsel vurderinger")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_OPPFYLT: Counter = Counter
            .builder(VURDERING_OPPFYLT)
            .description("Counts the number of successful oppfylt vurderinger")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_UTEN_FORHANDSVARSEL_OPPFYLT: Counter = Counter
            .builder(VURDERING_OPPFYLT_UTEN_FORHANDSVARSEL)
            .description("Counts the number of successful oppfylt vurderinger uten forhandsvarsel")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_AVSLAG: Counter = Counter
            .builder(VURDERING_AVSLAG)
            .description("Counts the number of successful avslag vurderinger")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_AVSLAG_UTEN_FORHANDSVARSEL: Counter = Counter
            .builder(VURDERING_AVSLAG_UTEN_FORHANDSVARSEL)
            .description("Counts the number of successful avslag uten forhandsvarsel vurderinger")
            .register(METRICS_REGISTRY)
        val COUNT_VURDERING_IKKE_AKTUELL: Counter = Counter
            .builder(VURDERING_IKKE_AKTUELL)
            .description("Counts the number of successful ikke-aktuell vurderinger")
            .register(METRICS_REGISTRY)
    }
}
