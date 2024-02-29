package no.nav.syfo.infrastructure.pdl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.Counter
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.ClientEnvironment
import no.nav.syfo.infrastructure.azuread.AzureAdClient
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.httpClientDefault
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import no.nav.syfo.infrastructure.pdl.dto.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val pdlEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    suspend fun getPerson(personident: PersonIdent): PdlPerson {
        val token = azureAdClient.getSystemToken(pdlEnvironment.clientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        val request = PdlHentPersonRequest(getPdlQuery(), PdlHentPersonRequestVariables(personident.value))

        val response: HttpResponse = httpClient.post(pdlEnvironment.baseUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(BEHANDLINGSNUMMER_HEADER_KEY, BEHANDLINGSNUMMER_HEADER_VALUE)
        }

        val person = when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.body<PdlPersonResponse>()
                if (!pdlPersonReponse.errors.isNullOrEmpty()) {
                    Metrics.COUNT_CALL_PDL_PERSON_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        logger.error("Error while requesting person from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    Metrics.COUNT_CALL_PDL_PERSON_SUCCESS.increment()
                    pdlPersonReponse.data?.hentPerson
                }
            }

            else -> {
                Metrics.COUNT_CALL_PDL_PERSON_FAIL.increment()
                logger.error("Request with url: ${pdlEnvironment.baseUrl} failed with reponse code ${response.status.value}")
                null
            }
        }

        return person ?: throw RuntimeException("PDL did not return a person for given fnr")
    }

    private fun getPdlQuery(): String =
        this::class.java.getResource(PDL_QUERY_PATH)!!
            .readText()
            .replace("[\n\r]", "")

    companion object {
        private const val PDL_QUERY_PATH = "/pdl/hentPerson.graphql"

        // Se behandlingskatalog https://behandlingskatalog.intern.nav.no/
        // Behandling: Sykefraværsoppfølging: Vurdere behov for oppfølging og rett til sykepenger etter §§ 8-4 og 8-8
        private const val BEHANDLINGSNUMMER_HEADER_KEY = "behandlingsnummer"
        private const val BEHANDLINGSNUMMER_HEADER_VALUE = "B426"

        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}

private class Metrics {
    companion object {
        const val CALL_PDL_PERSON_BASE = "${METRICS_NS}_call_pdl_person"
        const val CALL_PDL_PERSON_SUCCESS = "${CALL_PDL_PERSON_BASE}_success_count"
        const val CALL_PDL_PERSON_FAIL = "${CALL_PDL_PERSON_BASE}_fail_count"

        val COUNT_CALL_PDL_PERSON_SUCCESS: Counter = Counter.builder(CALL_PDL_PERSON_SUCCESS)
            .description("Counts the number of successful calls to pdl - person")
            .register(METRICS_REGISTRY)
        val COUNT_CALL_PDL_PERSON_FAIL: Counter = Counter.builder(CALL_PDL_PERSON_FAIL)
            .description("Counts the number of failed calls to pdl - person")
            .register(METRICS_REGISTRY)
    }
}
