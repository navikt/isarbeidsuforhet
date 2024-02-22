package no.nav.syfo.api.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.api.*
import no.nav.syfo.api.model.ForhandsvarselDTO
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ArbeidsuforhetEndpointsSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlForhandsvarsel = "$arbeidsuforhetApiBasePath/$forhandsvarselPath"

    describe(ArbeidsuforhetEndpointsSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
            val begrunnelse = "Dette er en begrunnelse for forhåndsvarsel 8-4"
            val forhandsvarselDTO = ForhandsvarselDTO(
                begrunnelse = begrunnelse,
                document = generateDocumentComponent(
                    fritekst = begrunnelse,
                    header = "Forhåndsvarsel"
                )
            )
            val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT.value

            describe("Forhåndsvarsel") {
                describe("Happy path") {
                    it("Successfully creates a new forhandsvarsel") {
                        with(
                            handleRequest(HttpMethod.Post, urlForhandsvarsel) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(forhandsvarselDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Fails if document is empty") {
                        val forhandsvarselWithoutDocument = forhandsvarselDTO.copy(document = emptyList())
                        with(
                            handleRequest(HttpMethod.Post, urlForhandsvarsel) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(forhandsvarselWithoutDocument))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlForhandsvarsel, HttpMethod.Post)
                    }
                    it("Returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlForhandsvarsel, validToken, HttpMethod.Post)
                    }
                    it("Returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlForhandsvarsel, validToken, HttpMethod.Post)
                    }
                    it("Returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlForhandsvarsel, validToken, HttpMethod.Post)
                    }
                }
            }
        }
    }
})
