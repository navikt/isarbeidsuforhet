package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.database.TestDatabase
import no.nav.syfo.infrastructure.database.TestDatabaseNotResponding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    val database = TestDatabase()
    val databaseNotResponding = TestDatabaseNotResponding()

    describe("Successful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                podEndpoints(
                    applicationState = ApplicationState(
                        alive = true,
                        ready = true
                    ),
                    database = database,
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
    describe("Unsuccessful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                podEndpoints(
                    ApplicationState(
                        alive = false,
                        ready = false
                    ),
                    database = database,
                )
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
    describe("Successful liveness and unsuccessful readiness checks when database not working") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                podEndpoints(
                    ApplicationState(
                        alive = true,
                        ready = true
                    ),
                    database = databaseNotResponding,
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
