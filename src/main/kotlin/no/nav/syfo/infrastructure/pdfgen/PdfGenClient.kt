package no.nav.syfo.infrastructure.pdfgen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.Counter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.api.httpClientDefault
import no.nav.syfo.infrastructure.NAV_CALL_ID_HEADER
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import org.slf4j.LoggerFactory

class PdfGenClient(
    private val httpClient: HttpClient = httpClientDefault(),
    private val pdfGenBaseUrl: String
) {

    suspend fun createForhandsvarselPdf(
        callId: String,
        payloadString: String, // TODO: Bytt ut med riktig objekt
    ): ByteArray =
        getPdf(
            callId = callId,
            payload = payloadString,
            pdfUrl = "$pdfGenBaseUrl$API_BASE_PATH$FORHANDSVARSEL_PATH"
        ) ?: throw RuntimeException("Failed to request pdf for forhandsvarsel, callId: $callId")

    private suspend inline fun <reified Payload> getPdf(
        callId: String,
        payload: Payload,
        pdfUrl: String,
    ): ByteArray? =
        try {
            val response: HttpResponse = httpClient.post(pdfUrl) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            Metrics.COUNT_CALL_PDFGEN_SUCCESS.increment()
            response.body()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(pdfUrl, e.response, callId)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(pdfUrl, e.response, callId)
        }

    private fun handleUnexpectedResponseException(
        url: String,
        response: HttpResponse,
        callId: String,
    ): ByteArray? {
        log.error(
            "Error while requesting PDF from isarbeidsuforhetpdfgen with {}, {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("url", url),
            StructuredArguments.keyValue("callId", callId),
        )
        Metrics.COUNT_CALL_PDFGEN_FAIL.increment()
        return null
    }

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isarbeidsuforhet"
        const val FORHANDSVARSEL_PATH = "/forhandsvarsel-til-innbygger-om-stans-av-sykepenger"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}

private object Metrics {
    private const val CALL_PDFGEN_BASE = "${METRICS_NS}_call_isarbeidsuforhetpdfgen"

    private const val CALL_PDFGEN_SUCCESS = "${CALL_PDFGEN_BASE}_success_count"
    private const val CALL_PDFGEN_FAIL = "${CALL_PDFGEN_BASE}_fail_count"

    val COUNT_CALL_PDFGEN_SUCCESS: Counter = Counter
        .builder(CALL_PDFGEN_SUCCESS)
        .description("Counts the number of successful calls to isarbeidsuforhetpdfgen")
        .register(METRICS_REGISTRY)
    val COUNT_CALL_PDFGEN_FAIL: Counter = Counter
        .builder(CALL_PDFGEN_FAIL)
        .description("Counts the number of failed calls to isarbeidsuforhetpdfgen")
        .register(METRICS_REGISTRY)
}
