package no.nav.syfo.infrastructure.veiledertilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.Counter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.api.ClientEnvironment
import no.nav.syfo.api.httpClientDefault
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.NAV_CALL_ID_HEADER
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.azuread.AzureAdClient
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault()
) {
    private val tilgangskontrollPersonUrl = "${clientEnvironment.baseUrl}$TILGANGSKONTROLL_PERSON_PATH"

    suspend fun hasAccess(
        callId: String,
        personIdent: PersonIdent,
        token: String
    ): Boolean {
        val onBehalfOfToken =
            azureAdClient.getOnBehalfOfToken(
                scopeClientId = clientEnvironment.clientId,
                token = token
            )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        return try {
            val tilgang =
                httpClient.get(tilgangskontrollPersonUrl) {
                    header(HttpHeaders.Authorization, bearerHeader(onBehalfOfToken))
                    header(NAV_PERSONIDENT_HEADER, personIdent.value)
                    header(NAV_CALL_ID_HEADER, callId)
                    accept(ContentType.Application.Json)
                }
            Metrics.COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.increment()
            tilgang.body<Tilgang>().erGodkjent
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                Metrics.COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, callId)
            }
            false
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String
    ) {
        log.error(
            "Error while requesting access to person from istilgangskontroll with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("callId", callId)
        )
        Metrics.COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.increment()
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)

        const val TILGANGSKONTROLL_PERSON_PATH = "/api/tilgang/navident/person"
    }
}

private class Metrics {
    companion object {
        const val CALL_TILGANGSKONTROLL_PERSON_BASE = "${METRICS_NS}_call_tilgangskontroll_person"
        const val CALL_TILGANGSKONTROLL_PERSON_SUCCESS = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_success_count"
        const val CALL_TILGANGSKONTROLL_PERSON_FAIL = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_fail_count"
        const val CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_forbidden_count"

        val COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS: Counter =
            Counter.builder(CALL_TILGANGSKONTROLL_PERSON_SUCCESS)
                .description("Counts the number of successful calls to istilgangskontroll - person")
                .register(METRICS_REGISTRY)
        val COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL: Counter =
            Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FAIL)
                .description("Counts the number of failed calls to istilgangskontroll - person")
                .register(METRICS_REGISTRY)
        val COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN: Counter =
            Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN)
                .description("Counts the number of forbidden calls to istilgangskontroll - person")
                .register(METRICS_REGISTRY)
    }
}
