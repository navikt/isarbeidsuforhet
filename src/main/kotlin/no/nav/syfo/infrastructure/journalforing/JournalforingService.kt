package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import org.slf4j.LoggerFactory

class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
    private val isJournalforingRetryEnabled: Boolean,
) : IJournalforingService {
    override suspend fun journalfor(
        personident: PersonIdent,
        pdf: ByteArray,
        vurdering: Vurdering,
    ): Int {
        val navn = pdlClient.getPerson(personident).fullName
        val journalpostRequest = createJournalpostRequest(
            personIdent = personident,
            navn = navn,
            pdf = pdf,
            vurdering = vurdering,
        )
        return try {
            dokarkivClient.journalfor(journalpostRequest).journalpostId
        } catch (exc: Exception) {
            if (isJournalforingRetryEnabled) {
                throw exc
            } else {
                log.error("Journalføring failed, skipping retry (should only happen in dev-gcp)", exc)
                // Defaulting'en til DEFAULT_FAILED_JP_ID skal bare forekomme i dev-gcp:
                // Har dette fordi vi ellers spammer ned dokarkiv med forsøk på å journalføre
                // på personer som mangler aktør-id.
                DEFAULT_FAILED_JP_ID
            }
        }
    }

    private fun createJournalpostRequest(
        personIdent: PersonIdent,
        navn: String,
        pdf: ByteArray,
        vurdering: Vurdering,
    ): JournalpostRequest {
        val journalpostType = vurdering.type.getJournalpostType()
        val avsenderMottaker = if (journalpostType != JournalpostType.NOTAT) {
            AvsenderMottaker.create(
                id = personIdent.value,
                idType = BrukerIdType.PERSON_IDENT,
                navn = navn,
            )
        } else null

        val bruker = Bruker.create(
            id = personIdent.value,
            idType = BrukerIdType.PERSON_IDENT,
        )

        val dokumentTittel = vurdering.type.getDokumentTittel()

        val dokumenter = listOf(
            Dokument.create(
                brevkode = vurdering.type.getBrevkode(),
                dokumentvarianter = listOf(
                    Dokumentvariant.create(
                        filnavn = dokumentTittel,
                        filtype = FiltypeType.PDFA,
                        fysiskDokument = pdf,
                        variantformat = VariantformatType.ARKIV,
                    )
                ),
                tittel = dokumentTittel,
            )
        )
        return JournalpostRequest(
            journalpostType = journalpostType.name,
            avsenderMottaker = avsenderMottaker,
            tittel = dokumentTittel,
            bruker = bruker,
            dokumenter = dokumenter,
            eksternReferanseId = vurdering.uuid.toString(),
        )
    }

    companion object {
        private const val DEFAULT_FAILED_JP_ID = 0
        private val log = LoggerFactory.getLogger(JournalforingService::class.java)
    }
}
