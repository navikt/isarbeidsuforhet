package no.nav.syfo.infrastructure.distribusjon

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.common.distribusjon.client.DokdistfordelingClient
import no.nav.syfo.common.distribusjon.dto.DistribuerJournalpostRequest
import no.nav.syfo.common.http.commonConfig
import no.nav.syfo.common.token.SystemTokenProvider
import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.infrastructure.mock.receiveBody
import no.nav.syfo.testEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DistribusjonServiceTest {
    private val environment = testEnvironment().clients.dokdistfordeling
    private val systemTokenProvider = mockk<SystemTokenProvider> {
        coEvery { getSystemToken(environment.clientId) } returns "token"
    }

    @Test
    fun `orders distribution of forhandsvarsel with system token`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("${environment.baseUrl}/rest/v1/distribuerjournalpost", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertEquals(ContentType.Application.Json, request.body.contentType)
            val body = request.receiveBody<DistribuerJournalpostRequest>()
            assertEquals("123", body.journalpostId)
            assertEquals("MODIA_SYFO", body.bestillendeFagsystem)
            assertEquals("isarbeidsuforhet", body.dokumentProdApp)
            assertEquals("VIKTIG", body.distribusjonstype)
            assertEquals("KJERNETID", body.distribusjonstidspunkt)
            respond(
                """{"bestillingsId":"bestilling-123"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        assertEquals("bestilling-123", client(engine).distribuer(JournalpostId("123")))
        coVerify(exactly = 1) { systemTokenProvider.getSystemToken(environment.clientId) }
    }

    @Test
    fun `returns existing order when journalpost was already distributed`() = runBlocking {
        val engine = MockEngine {
            respond(
                """{"bestillingsId":"existing-bestilling"}""",
                HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        assertEquals("existing-bestilling", client(engine).distribuer(JournalpostId("123")))
    }

    @Test
    fun `propagates unexpected client and server errors`() = runBlocking {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)) {
            val engine = MockEngine { respond("Failed", status) }
            assertThrows<ResponseException> { client(engine).distribuer(JournalpostId("123")) }
        }
    }

    @Test
    fun `does not order distribution without a system token`() = runBlocking {
        coEvery { systemTokenProvider.getSystemToken(environment.clientId) } returns null
        val engine = MockEngine { error("Dokdist should not be called without a token") }

        assertThrows<IllegalStateException> { client(engine).distribuer(JournalpostId("123")) }
        assertEquals(0, engine.requestHistory.size)
    }

    private fun client(engine: MockEngine) = DistribusjonService(
        dokdistfordelingClient = DokdistfordelingClient(
            systemTokenProvider = systemTokenProvider,
            clientConfig = ClientConfig(baseUrl = environment.baseUrl, clientId = environment.clientId),
            httpClient = HttpClient(engine) { commonConfig() },
        ),
    )
}
