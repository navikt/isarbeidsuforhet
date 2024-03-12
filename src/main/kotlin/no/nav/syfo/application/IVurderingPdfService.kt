package no.nav.syfo.application

import no.nav.syfo.domain.Vurdering

interface IVurderingPdfService {
    suspend fun createVurderingPdf(
        vurdering: Vurdering,
        callId: String,
    ): ByteArray
}
