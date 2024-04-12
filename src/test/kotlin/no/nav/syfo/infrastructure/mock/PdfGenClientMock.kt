package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.pdfgen.PdfGenClient

fun MockRequestHandleScope.pdfGenClientMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(PdfGenClient.Companion.FORHANDSVARSEL_PATH) -> {
            respond(content = UserConstants.PDF_FORHANDSVARSEL)
        }
        requestUrl.endsWith(PdfGenClient.Companion.VURDERING_PATH) -> {
            respond(content = UserConstants.PDF_VURDERING)
        }
        requestUrl.endsWith(PdfGenClient.Companion.AVSLAG_PATH) -> {
            respond(content = UserConstants.PDF_AVSLAG)
        }
        else -> error("Unhandled pdf ${request.url.encodedPath}")
    }
}
