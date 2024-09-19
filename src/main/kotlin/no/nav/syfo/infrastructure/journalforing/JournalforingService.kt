package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient

class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
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

        return dokarkivClient.journalfor(journalpostRequest).journalpostId
    }

    private fun createJournalpostRequest(
        personIdent: PersonIdent,
        navn: String,
        pdf: ByteArray,
        vurdering: Vurdering,
    ): JournalpostRequest {
        val avsenderMottaker = AvsenderMottaker.create(
            id = personIdent.value,
            idType = BrukerIdType.PERSON_IDENT,
            navn = navn,
        )
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
        val journalpostType = vurdering.type.getJournalpostType()
        return JournalpostRequest(
            journalpostType = journalpostType.name,
            avsenderMottaker = if (journalpostType == JournalpostType.UTGAAENDE) avsenderMottaker else null,
            tittel = dokumentTittel,
            bruker = bruker,
            dokumenter = dokumenter,
            eksternReferanseId = vurdering.uuid.toString(),
        )
    }
}
