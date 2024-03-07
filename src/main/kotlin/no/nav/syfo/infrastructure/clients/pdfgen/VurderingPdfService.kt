package no.nav.syfo.infrastructure.clients.pdfgen

import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.clients.pdl.PdlClient

class VurderingPdfService(
    private val pdfGenClient: PdfGenClient,
    private val pdlClient: PdlClient,
) : IVurderingPdfService {

    override suspend fun createVurderingPdf(
        personident: PersonIdent,
        document: List<DocumentComponent>,
        callId: String,
    ): ByteArray {
        val personNavn = pdlClient.getPerson(personident).fullName
        val vurderingPdfDTO = VurderingPdfDTO.create(
            documentComponents = document,
            mottakerNavn = personNavn,
            mottakerPersonident = personident,
        )

        return pdfGenClient.createForhandsvarselPdf(
            callId = callId,
            forhandsvarselPdfDTO = vurderingPdfDTO,
        )
    }
}
