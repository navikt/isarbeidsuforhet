package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent

interface IVurderingPdfService {
    suspend fun createVurderingPdf(
        personident: PersonIdent,
        document: List<DocumentComponent>,
        callId: String,
    ): ByteArray
}
