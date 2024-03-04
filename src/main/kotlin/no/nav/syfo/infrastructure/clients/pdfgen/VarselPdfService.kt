package no.nav.syfo.infrastructure.clients.pdfgen

import no.nav.syfo.application.IVarselPdfService
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.clients.pdl.PdlClient

class VarselPdfService(
    private val pdfGenClient: PdfGenClient,
    private val pdlClient: PdlClient,
) : IVarselPdfService {

    override suspend fun createVarselPdf(
        personident: PersonIdent,
        document: List<DocumentComponent>,
        callId: String,
    ): ByteArray {
        val personNavn = pdlClient.getPerson(personident).fullName
        val varselPdfDTO = VarselPdfDTO.create(
            documentComponents = document,
            mottakerNavn = personNavn,
            mottakerPersonident = personident,
        )

        return pdfGenClient.createForhandsvarselPdf(
            callId = callId,
            forhandsvarselPdfDTO = varselPdfDTO,
        )
    }
}
