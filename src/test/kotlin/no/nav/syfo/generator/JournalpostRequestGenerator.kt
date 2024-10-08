package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import java.util.UUID

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    vurderingUuid: UUID,
    journalpostType: String = JournalpostType.UTGAAENDE.name,
) = JournalpostRequest(
    avsenderMottaker = if (journalpostType == JournalpostType.UTGAAENDE.name)
        AvsenderMottaker.create(
            id = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
            idType = BrukerIdType.PERSON_IDENT,
            navn = UserConstants.PERSON_FULLNAME,
        ) else null,
    bruker = Bruker.create(
        id = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
        idType = BrukerIdType.PERSON_IDENT
    ),
    tittel = tittel,
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    journalpostType = journalpostType,
    eksternReferanseId = vurderingUuid.toString(),
)
