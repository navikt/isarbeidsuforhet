package no.nav.syfo.api.endpoints

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.TestDatabase
import no.nav.syfo.infrastructure.database.TestDatabaseNotResponding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.Test

class PodApiTest {

    private val database = TestDatabase()
    private val databaseNotResponding = TestDatabaseNotResponding()

    private fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                podEndpoints(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    @Nested
    @DisplayName("Successful liveness and readiness checks")
    inner class SuccessfulLivenessAndReadinessChecks {
        @Test
        fun `returns ok on is_alive when application is alive and ready`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get("/internal/is_alive")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `returns ok on is_ready when application is alive and ready`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get("/internal/is_ready")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Unsuccessful liveness and readiness checks")
    inner class UnsuccessfulLivenessAndReadinessChecks {
        @Test
        fun `returns internal server error on is_alive when application not alive`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = false, ready = false)
            )
            val response = client.get("/internal/is_alive")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `returns internal server error on is_ready when application not ready`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = false, ready = false)
            )
            val response = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Successful liveness and unsuccessful readiness checks when database not working")
    inner class SuccessfulLivenessAndReadinessWhenDatabaseNotResponding {
        @Test
        fun `returns ok on is_alive when database not responding`() = testApplication {
            setupPodApi(
                database = databaseNotResponding,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get("/internal/is_alive")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `returns internal server error on is_ready when database not responding`() = testApplication {
            setupPodApi(
                database = databaseNotResponding,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }
}
