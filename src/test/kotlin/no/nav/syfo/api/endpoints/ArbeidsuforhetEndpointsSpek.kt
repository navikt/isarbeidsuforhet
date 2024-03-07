package no.nav.syfo.api.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.api.*
import no.nav.syfo.api.model.ForhandsvarselRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.getVurderingPdf
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

object ArbeidsuforhetEndpointsSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlForhandsvarsel = "$arbeidsuforhetApiBasePath/$forhandsvarselPath"
    val urlVurdering = "$arbeidsuforhetApiBasePath/$vurderingPath"

    describe(ArbeidsuforhetEndpointsSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )
            val journalforingService = JournalforingService(
                dokarkivClient = externalMockEnvironment.dokarkivClient,
                pdlClient = externalMockEnvironment.pdlClient,
            )

            val vurderingRepository = VurderingRepository(database)
            val vurderingService = VurderingService(
                vurderingRepository = vurderingRepository,
                vurderingPdfService = VurderingPdfService(
                    pdfGenClient = externalMockEnvironment.pdfgenClient,
                    pdlClient = externalMockEnvironment.pdlClient,
                ),
                journalforingService = journalforingService,
            )
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = VEILEDER_IDENT,
            )
            val begrunnelse = "Dette er en begrunnelse for forhåndsvarsel 8-4"
            val document = generateDocumentComponent(
                fritekst = begrunnelse,
                header = "Forhåndsvarsel"
            )
            val forhandsvarselRequestDTO = ForhandsvarselRequestDTO(
                begrunnelse = begrunnelse,
                document = document,
            )
            val personIdent = ARBEIDSTAKER_PERSONIDENT.value

            beforeEachTest {
                database.dropData()
            }

            describe("Forhåndsvarsel") {
                describe("Happy path") {
                    it("Successfully creates a new forhandsvarsel") {
                        with(
                            handleRequest(HttpMethod.Post, urlForhandsvarsel) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(forhandsvarselRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created

                            val responseDTO = objectMapper.readValue<VurderingResponseDTO>(response.content!!)
                            responseDTO.begrunnelse shouldBeEqualTo begrunnelse
                            responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                            responseDTO.document shouldBeEqualTo document

                            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).firstOrNull()
                            vurdering?.begrunnelse shouldBeEqualTo begrunnelse
                            vurdering?.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT

                            val pVurderingPdf = database.getVurderingPdf(vurdering!!.uuid)
                            pVurderingPdf?.pdf?.size shouldBeEqualTo PDF_FORHANDSVARSEL.size
                            pVurderingPdf?.pdf?.get(0) shouldBeEqualTo PDF_FORHANDSVARSEL[0]
                            pVurderingPdf?.pdf?.get(1) shouldBeEqualTo PDF_FORHANDSVARSEL[1]
                        }
                    }
                    it("Does not allow duplicate forhandsvarsel") {
                        runBlocking {
                            vurderingService.createForhandsvarsel(
                                personident = PersonIdent(personIdent),
                                veilederident = VEILEDER_IDENT,
                                begrunnelse = begrunnelse,
                                document = document,
                                callId = UUID.randomUUID().toString(),
                            )
                        }
                        with(
                            handleRequest(HttpMethod.Post, urlForhandsvarsel) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(forhandsvarselRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Successfully gets an existing vurdering") {
                        runBlocking {
                            vurderingService.createForhandsvarsel(
                                personident = PersonIdent(personIdent),
                                veilederident = VEILEDER_IDENT,
                                begrunnelse = begrunnelse,
                                document = document,
                                callId = UUID.randomUUID().toString(),
                            )
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlVurdering) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOs = objectMapper.readValue<List<VurderingResponseDTO>>(response.content!!)
                            responseDTOs.size shouldBeEqualTo 1

                            val responseDTO = responseDTOs.first()
                            responseDTO.begrunnelse shouldBeEqualTo begrunnelse
                            responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                            responseDTO.document shouldBeEqualTo document
                            responseDTO.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                        }
                    }
                    it("Successfully gets empty list of vurderinger") {
                        with(
                            handleRequest(HttpMethod.Get, urlVurdering) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOs = objectMapper.readValue<List<VurderingResponseDTO>>(response.content!!)
                            responseDTOs.size shouldBeEqualTo 0
                        }
                    }
                    it("Successfully gets multiple vurderinger") {
                        runBlocking {
                            vurderingService.createForhandsvarsel(
                                personident = PersonIdent(personIdent),
                                veilederident = VEILEDER_IDENT,
                                begrunnelse = begrunnelse,
                                document = document,
                                callId = UUID.randomUUID().toString(),
                            )
                            vurderingService.createForhandsvarsel(
                                personident = PersonIdent(personIdent),
                                veilederident = VEILEDER_IDENT,
                                begrunnelse = begrunnelse,
                                document = document,
                                callId = UUID.randomUUID().toString(),
                            )
                        }
                        with(
                            handleRequest(HttpMethod.Get, urlVurdering) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOs = objectMapper.readValue<List<VurderingResponseDTO>>(response.content!!)
                            responseDTOs.size shouldBeEqualTo 2
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Fails if document is empty") {
                        val forhandsvarselWithoutDocument = forhandsvarselRequestDTO.copy(document = emptyList())
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
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlVurdering, HttpMethod.Get)
                    }
                    it("Returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlVurdering, validToken, HttpMethod.Get)
                    }
                    it("Returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlVurdering, validToken, HttpMethod.Get)
                    }
                    it("Returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlVurdering, validToken, HttpMethod.Get)
                    }
                }
            }
        }
    }
})
