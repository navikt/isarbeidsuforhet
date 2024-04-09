package no.nav.syfo.api.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.api.*
import no.nav.syfo.api.model.VurderingRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.application.IVurderingProducer
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
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

object ArbeidsuforhetEndpointsSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()
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
                vurderingProducer = mockk<IVurderingProducer>(),
            )
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = VEILEDER_IDENT,
            )
            val begrunnelse = "Dette er en begrunnelse for vurdering av 8-4"
            val forhandsvarselDocument = generateDocumentComponent(
                fritekst = begrunnelse,
                header = "Forhåndsvarsel"
            )
            val vurderingDocumentOppfylt = generateDocumentComponent(
                fritekst = begrunnelse,
                header = "Oppfylt",
            )
            val forhandsvarselRequestDTO = VurderingRequestDTO(
                type = VurderingType.FORHANDSVARSEL,
                begrunnelse = begrunnelse,
                document = forhandsvarselDocument,
            )
            val vurderingRequestDTO = VurderingRequestDTO(
                type = VurderingType.OPPFYLT,
                begrunnelse = begrunnelse,
                document = vurderingDocumentOppfylt,
            )
            val personIdent = ARBEIDSTAKER_PERSONIDENT.value

            suspend fun createForhandsvarsel() = vurderingService.createVurdering(
                personident = PersonIdent(personIdent),
                veilederident = VEILEDER_IDENT,
                type = VurderingType.FORHANDSVARSEL,
                begrunnelse = begrunnelse,
                document = forhandsvarselDocument,
                gjelderFom = null,
                callId = UUID.randomUUID().toString(),
            )

            beforeEachTest {
                database.dropData()
            }

            describe("Vurdering") {
                describe("Happy path") {
                    it("Successfully creates a new forhandsvarsel with varsel and pdf") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
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
                            responseDTO.document shouldBeEqualTo forhandsvarselDocument
                            responseDTO.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                            responseDTO.gjelderFom.shouldBeNull()
                            responseDTO.varsel shouldNotBeEqualTo null

                            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
                            vurdering.begrunnelse shouldBeEqualTo begrunnelse
                            vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                            vurdering.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                            vurdering.gjelderFom.shouldBeNull()
                            vurdering.varsel shouldNotBeEqualTo null

                            val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
                            pVurderingPdf?.pdf?.size shouldBeEqualTo PDF_FORHANDSVARSEL.size
                            pVurderingPdf?.pdf?.get(0) shouldBeEqualTo PDF_FORHANDSVARSEL[0]
                            pVurderingPdf?.pdf?.get(1) shouldBeEqualTo PDF_FORHANDSVARSEL[1]
                        }
                    }
                    it("Does not allow duplicate forhandsvarsel") {
                        runBlocking { createForhandsvarsel() }
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(forhandsvarselRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Creates new vurdering OPPFYLT and creates PDF") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(vurderingRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created

                            val responseDTO = objectMapper.readValue<VurderingResponseDTO>(response.content!!)
                            responseDTO.begrunnelse shouldBeEqualTo begrunnelse
                            responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                            responseDTO.document shouldBeEqualTo vurderingDocumentOppfylt
                            responseDTO.type shouldBeEqualTo VurderingType.OPPFYLT
                            responseDTO.gjelderFom.shouldBeNull()
                            responseDTO.varsel shouldBeEqualTo null

                            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
                            vurdering.begrunnelse shouldBeEqualTo begrunnelse
                            vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                            vurdering.type shouldBeEqualTo VurderingType.OPPFYLT
                            vurdering.gjelderFom.shouldBeNull()
                            vurdering.varsel shouldBeEqualTo null

                            val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
                            pVurderingPdf?.pdf?.size shouldBeEqualTo PDF_VURDERING.size
                            pVurderingPdf?.pdf?.get(0) shouldBeEqualTo PDF_VURDERING[0]
                            pVurderingPdf?.pdf?.get(1) shouldBeEqualTo PDF_VURDERING[1]
                        }
                    }
                    it("Creates new vurdering AVSLAG and do not create PDF") {
                        val avslagGjelderFom = LocalDate.now().plusDays(1)
                        val vurderingAvslagRequestDTO = VurderingRequestDTO(
                            type = VurderingType.AVSLAG,
                            begrunnelse = "",
                            document = emptyList(),
                            gjelderFom = avslagGjelderFom
                        )
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(vurderingAvslagRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created

                            val responseDTO = objectMapper.readValue<VurderingResponseDTO>(response.content!!)
                            responseDTO.begrunnelse shouldBeEqualTo ""
                            responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                            responseDTO.document shouldBeEqualTo emptyList()
                            responseDTO.type shouldBeEqualTo VurderingType.AVSLAG
                            responseDTO.gjelderFom shouldBeEqualTo avslagGjelderFom
                            responseDTO.varsel shouldBeEqualTo null

                            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
                            vurdering.begrunnelse shouldBeEqualTo ""
                            vurdering.document shouldBeEqualTo emptyList()
                            vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                            vurdering.type shouldBeEqualTo VurderingType.AVSLAG
                            vurdering.gjelderFom shouldBeEqualTo avslagGjelderFom
                            vurdering.varsel shouldBeEqualTo null

                            val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
                            pVurderingPdf shouldBeEqualTo null
                        }
                    }
                    it("Successfully gets an existing vurdering") {
                        runBlocking { createForhandsvarsel() }
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
                            responseDTO.document shouldBeEqualTo forhandsvarselDocument
                            responseDTO.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                            responseDTO.varsel.shouldNotBeNull()
                            responseDTO.varsel?.svarfrist shouldBeEqualTo LocalDate.now().plusWeeks(3)
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
                            createForhandsvarsel()
                            createForhandsvarsel()
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
                    it("Throws error when document is empty") {
                        val vurderingWithoutDocument = vurderingRequestDTO.copy(document = emptyList())
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(vurderingWithoutDocument))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Throws error when begrunnelse is empty") {
                        val vurderingWithoutBegrunnelse = vurderingRequestDTO.copy(begrunnelse = "")
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(vurderingWithoutBegrunnelse))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
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
