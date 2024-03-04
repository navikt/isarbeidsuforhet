package no.nav.syfo.infrastructure.clients.dokarkiv

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.Counter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostRequest
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostResponse
import no.nav.syfo.infrastructure.clients.httpClientDefault
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import org.slf4j.LoggerFactory

class DokarkivClient(
    private val azureAdClient: AzureAdClient,
    private val dokarkivEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val journalpostUrl: String = "${dokarkivEnvironment.baseUrl}$JOURNALPOST_PATH"

    suspend fun journalfor(
        journalpostRequest: JournalpostRequest,
    ): JournalpostResponse {
        val token = azureAdClient.getSystemToken(dokarkivEnvironment.clientId)?.accessToken
            ?: throw RuntimeException("Failed to Journalfor Journalpost: No token was found")
        return try {
            val response: HttpResponse = httpClient.post(journalpostUrl) {
                parameter(JOURNALPOST_PARAM_STRING, JOURNALPOST_PARAM_VALUE)
                header(HttpHeaders.Authorization, bearerHeader(token))
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(journalpostRequest)
            }
            val journalpostResponse = response.body<JournalpostResponse>()
            Metrics.COUNT_CALL_DOKARKIV_JOURNALPOST_SUCCESS.increment()
            journalpostResponse
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Conflict) {
                val journalpostResponse = e.response.body<JournalpostResponse>()
                log.warn("Journalpost med id ${journalpostResponse.journalpostId} lagret fra før (409 Conflict)")
                Metrics.COUNT_CALL_DOKARKIV_JOURNALPOST_CONFLICT.increment()
                journalpostResponse
            } else {
                handleUnexpectedResponseException(e.response, e.message)
                throw e
            }
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, e.message)
            throw e
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        message: String?,
    ) {
        log.error(
            "Error while requesting Dokarkiv to Journalpost PDF with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("message", message),
        )
        Metrics.COUNT_CALL_DOKARKIV_JOURNALPOST_FAIL.increment()
    }

    companion object {
        private const val JOURNALPOST_PATH = "/rest/journalpostapi/v1/journalpost"
        private const val JOURNALPOST_PARAM_STRING = "forsoekFerdigstill"
        private const val JOURNALPOST_PARAM_VALUE = true
        private val log = LoggerFactory.getLogger(DokarkivClient::class.java)
    }
}

private class Metrics {
    companion object {
        const val CALL_DOKARKIV_BASE = "${METRICS_NS}_call_dokarkiv"

        const val CALL_DOKARKIV_JOURNALPOST_BASE = "${CALL_DOKARKIV_BASE}_journalpost"
        const val CALL_DOKARKIV_JOURNALPOST_SUCCESS = "${CALL_DOKARKIV_JOURNALPOST_BASE}_success_count"
        const val CALL_DOKARKIV_JOURNALPOST_FAIL = "${CALL_DOKARKIV_JOURNALPOST_BASE}_fail_count"

        val COUNT_CALL_DOKARKIV_JOURNALPOST_SUCCESS: Counter = Counter
            .builder(CALL_DOKARKIV_JOURNALPOST_SUCCESS)
            .description("Counts the number of successful calls to Dokarkiv - Journalpost")
            .register(METRICS_REGISTRY)
        val COUNT_CALL_DOKARKIV_JOURNALPOST_FAIL: Counter = Counter
            .builder(CALL_DOKARKIV_JOURNALPOST_FAIL)
            .description("Counts the number of failed calls to Dokarkiv - Journalpost")
            .register(METRICS_REGISTRY)

        const val CALL_DOKARKIV_JOURNALPOST_CONFLICT = "${CALL_DOKARKIV_JOURNALPOST_BASE}_conflict_count"
        val COUNT_CALL_DOKARKIV_JOURNALPOST_CONFLICT: Counter = Counter
            .builder(CALL_DOKARKIV_JOURNALPOST_CONFLICT)
            .description("Counts the number of calls to Dokarkiv - Journalpost resulting in 409 Conflict")
            .register(METRICS_REGISTRY)
    }
}
