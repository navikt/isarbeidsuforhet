package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import java.util.*

class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
) : IJournalforingService {
    override suspend fun journalfor(
        personident: PersonIdent,
        pdf: ByteArray,
        varselUUID: UUID,
    ): Int {
        val navn = pdlClient.getPerson(personident).fullName
        val journalpostRequest = createJournalpostRequest(
            personIdent = personident,
            navn = navn,
            pdf = pdf,
            varselUuid = varselUUID,
        )

        return dokarkivClient.journalfor(journalpostRequest).journalpostId
    }

    private fun createJournalpostRequest(
        personIdent: PersonIdent,
        navn: String,
        pdf: ByteArray,
        varselUuid: UUID,
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

        val dokumentTittel = "Forhåndsvarsel om stans av sykepenger"

        val dokumenter = listOf(
            Dokument.create(
                brevkode = BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL,
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
            journalpostType = JournalpostType.UTGAAENDE.name,
            avsenderMottaker = avsenderMottaker,
            tittel = dokumentTittel,
            bruker = bruker,
            dokumenter = dokumenter,
            eksternReferanseId = varselUuid.toString(),
        )
    }
}
