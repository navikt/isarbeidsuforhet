package no.nav.syfo.api.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_2_PERSONIDENT
import no.nav.syfo.UserConstants.ARBEIDSTAKER_3_PERSONIDENT
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.UserConstants.PDF_AVSLAG
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.api.*
import no.nav.syfo.api.model.VurderingRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.api.model.VurderingerRequestDTO
import no.nav.syfo.api.model.VurderingerResponseDTO
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.*
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.getVurderingPdf
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.*
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
            val vurderingDocumentAvslag = generateDocumentComponent(
                fritekst = begrunnelse,
                header = "Avslag",
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

            suspend fun createVurdering(
                personident: PersonIdent = PersonIdent(personIdent),
                veilederident: String = VEILEDER_IDENT,
                type: VurderingType,
                document: List<DocumentComponent>
            ) = vurderingService.createVurdering(
                personident = personident,
                veilederident = veilederident,
                type = type,
                arsak = null,
                begrunnelse = begrunnelse,
                document = document,
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
                        runBlocking {
                            createVurdering(
                                type = VurderingType.FORHANDSVARSEL,
                                document = forhandsvarselDocument
                            )
                        }
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
                            responseDTO.arsak.shouldBeNull()
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
                    it("Creates new vurdering AVSLAG and creates PDF") {
                        val expiredForhandsvarsel =
                            generateForhandsvarselVurdering().copy(varsel = Varsel().copy(svarfrist = LocalDate.now().minusDays(1)))
                        vurderingRepository.createVurdering(
                            vurdering = expiredForhandsvarsel,
                            pdf = PDF_FORHANDSVARSEL,
                        )
                        val avslagGjelderFom = LocalDate.now().plusDays(1)
                        val vurderingAvslagRequestDTO = VurderingRequestDTO(
                            type = VurderingType.AVSLAG,
                            begrunnelse = "Avslag",
                            document = vurderingDocumentAvslag,
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
                            responseDTO.begrunnelse shouldBeEqualTo "Avslag"
                            responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                            responseDTO.document shouldBeEqualTo vurderingDocumentAvslag
                            responseDTO.type shouldBeEqualTo VurderingType.AVSLAG
                            responseDTO.arsak.shouldBeNull()
                            responseDTO.gjelderFom shouldBeEqualTo avslagGjelderFom
                            responseDTO.varsel shouldBeEqualTo null

                            val vurderinger = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
                            vurderinger.size shouldBeEqualTo 2
                            val avslagVurdering = vurderinger.first()
                            avslagVurdering.begrunnelse shouldBeEqualTo "Avslag"
                            avslagVurdering.document shouldBeEqualTo vurderingDocumentAvslag
                            avslagVurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                            avslagVurdering.type shouldBeEqualTo VurderingType.AVSLAG
                            avslagVurdering.gjelderFom shouldBeEqualTo avslagGjelderFom
                            avslagVurdering.varsel shouldBeEqualTo null

                            val pVurderingPdf = database.getVurderingPdf(avslagVurdering.uuid)
                            pVurderingPdf?.pdf?.size shouldBeEqualTo PDF_AVSLAG.size
                            pVurderingPdf?.pdf?.get(0) shouldBeEqualTo PDF_AVSLAG[0]
                            pVurderingPdf?.pdf?.get(1) shouldBeEqualTo PDF_AVSLAG[1]
                        }
                    }

                    it("Creates new vurdering IKKE_AKTUELL and creates PDF") {
                        val ikkeAktuellRequestDTO = VurderingRequestDTO(
                            type = VurderingType.IKKE_AKTUELL,
                            begrunnelse = "",
                            document = generateDocumentComponent(fritekst = ""),
                            arsak = VurderingArsak.FRISKMELDT,
                        )
                        with(
                            handleRequest(HttpMethod.Post, urlVurdering) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(ikkeAktuellRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created

                            val responseDTO = objectMapper.readValue<VurderingResponseDTO>(response.content!!)
                            responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                            responseDTO.type shouldBeEqualTo VurderingType.IKKE_AKTUELL
                            responseDTO.arsak shouldBeEqualTo VurderingArsak.FRISKMELDT
                            responseDTO.document.shouldNotBeEmpty()
                            responseDTO.begrunnelse.shouldBeEmpty()
                            responseDTO.gjelderFom.shouldBeNull()
                            responseDTO.varsel.shouldBeNull()

                            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
                            vurdering.begrunnelse.shouldBeEmpty()
                            vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                            vurdering.type shouldBeEqualTo VurderingType.IKKE_AKTUELL
                            vurdering.gjelderFom.shouldBeNull()
                            vurdering.varsel.shouldBeNull()

                            val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
                            pVurderingPdf?.pdf?.size shouldBeEqualTo PDF_VURDERING.size
                            pVurderingPdf?.pdf?.get(0) shouldBeEqualTo PDF_VURDERING[0]
                            pVurderingPdf?.pdf?.get(1) shouldBeEqualTo PDF_VURDERING[1]
                        }
                    }

                    it("Successfully gets an existing vurdering") {
                        runBlocking {
                            createVurdering(
                                type = VurderingType.FORHANDSVARSEL,
                                document = forhandsvarselDocument
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
                            responseDTO.document shouldBeEqualTo forhandsvarselDocument
                            responseDTO.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                            responseDTO.arsak.shouldBeNull()
                            responseDTO.varsel.shouldNotBeNull()
                            responseDTO.varsel?.svarfrist shouldBeEqualTo LocalDate.now().plusWeeks(3)
                            responseDTO.varsel?.isExpired shouldBeEqualTo false
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
                            createVurdering(type = VurderingType.FORHANDSVARSEL, document = forhandsvarselDocument)
                            createVurdering(type = VurderingType.OPPFYLT, document = generateDocumentComponent("Oppfylt"))
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
                describe("POST: Get vurderinger for several persons") {
                    val personidenter = listOf(ARBEIDSTAKER_PERSONIDENT, ARBEIDSTAKER_2_PERSONIDENT, ARBEIDSTAKER_3_PERSONIDENT)
                    val requestDTO = VurderingerRequestDTO(personidenter.map { it.value })
                    val url = "$arbeidsuforhetApiBasePath/get-vurderinger"

                    suspend fun createVurderinger(
                        identer: List<PersonIdent> = personidenter,
                        type: VurderingType = VurderingType.FORHANDSVARSEL,
                    ) {
                        identer.forEach { personident ->
                            createVurdering(
                                personident = personident,
                                veilederident = VEILEDER_IDENT,
                                type = type,
                                document = forhandsvarselDocument,
                            )
                        }
                    }

                    it("Gets all vurderinger for all persons") {
                        runBlocking { createVurderinger() }

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val responseDTO =
                                objectMapper.readValue<VurderingerResponseDTO>(response.content!!)

                            responseDTO.vurderinger.size shouldBeEqualTo 3
                            responseDTO.vurderinger.keys shouldContainAll personidenter.map { it.value }
                            responseDTO.vurderinger.forEach { (_, vurdering) ->
                                vurdering.veilederident shouldBeEqualTo VEILEDER_IDENT
                                vurdering.begrunnelse shouldBeEqualTo begrunnelse
                                vurdering.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                                vurdering.varsel shouldNotBe null
                            }
                        }
                    }
                    it("Gets latest vurderinger for all persons") {
                        runBlocking {
                            createVurderinger()
                            createVurderinger(
                                identer = listOf(ARBEIDSTAKER_PERSONIDENT),
                                type = VurderingType.OPPFYLT,
                            )
                            createVurderinger(
                                identer = listOf(ARBEIDSTAKER_2_PERSONIDENT),
                                type = VurderingType.OPPFYLT,
                            )
                            createVurderinger(
                                identer = listOf(ARBEIDSTAKER_2_PERSONIDENT),
                                type = VurderingType.FORHANDSVARSEL,
                            )
                        }

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val responseDTO =
                                objectMapper.readValue<VurderingerResponseDTO>(response.content!!)

                            responseDTO.vurderinger.size shouldBeEqualTo 3
                            responseDTO.vurderinger.keys shouldContainAll personidenter.map { it.value }
                            responseDTO.vurderinger.forEach { (_, vurdering) ->
                                vurdering.veilederident shouldBeEqualTo VEILEDER_IDENT
                                vurdering.begrunnelse shouldBeEqualTo begrunnelse
                                if (vurdering.personident == ARBEIDSTAKER_PERSONIDENT.value) {
                                    vurdering.type shouldBeEqualTo VurderingType.OPPFYLT
                                    vurdering.varsel shouldBe null
                                } else {
                                    vurdering.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                                    vurdering.varsel shouldNotBe null
                                }
                            }
                        }
                    }

                    it("Gets vurderinger only for person with vurdering, even when veileder has access to all persons") {
                        runBlocking { createVurderinger(identer = listOf(ARBEIDSTAKER_PERSONIDENT)) }

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val responseDTO =
                                objectMapper.readValue<VurderingerResponseDTO>(response.content!!)

                            responseDTO.vurderinger.size shouldBeEqualTo 1
                            responseDTO.vurderinger.keys shouldContain ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.vurderinger.forEach { (_, vurdering) ->
                                vurdering.veilederident shouldBeEqualTo VEILEDER_IDENT
                                vurdering.begrunnelse shouldBeEqualTo begrunnelse
                            }
                        }
                    }

                    it("Gets vurderinger only for persons where veileder has access") {
                        val personidenter = listOf(ARBEIDSTAKER_PERSONIDENT, ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS)
                        val requestDTO = VurderingerRequestDTO(personidenter.map { it.value })
                        runBlocking { createVurderinger(identer = personidenter) }

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val responseDTO = objectMapper.readValue<VurderingerResponseDTO>(response.content!!)

                            responseDTO.vurderinger.size shouldBeEqualTo 1
                            responseDTO.vurderinger.keys shouldContain ARBEIDSTAKER_PERSONIDENT.value
                            responseDTO.vurderinger.keys shouldNotContain ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value
                        }
                    }

                    it("Gets no vurderinger when none of the persons has vurdering") {
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.NoContent
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
