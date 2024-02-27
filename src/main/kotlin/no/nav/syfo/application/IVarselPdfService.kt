package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent

interface IVarselPdfService {
    suspend fun createVarselPdf(
        personident: PersonIdent,
        document: List<DocumentComponent>,
        callId: String,
    ): ByteArray
}
