package no.nav.syfo.api.endpoints

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
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
import no.nav.syfo.api.generateJWT
import no.nav.syfo.domain.VurderingArsak
import no.nav.syfo.api.model.VurderingRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.api.model.VurderingerRequestDTO
import no.nav.syfo.api.model.VurderingerResponseDTO
import no.nav.syfo.api.testApiModule
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVurderingPdf
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.util.configure
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

object ArbeidsuforhetEndpointsSpek : Spek({

    val urlVurdering = "$arbeidsuforhetApiBasePath/$vurderingPath"

    describe(ArbeidsuforhetEndpointsSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
            application {
                testApiModule(
                    externalMockEnvironment = ExternalMockEnvironment.instance,
                )
            }
            val client = createClient {
                install(ContentNegotiation) {
                    jackson { configure() }
                }
            }

            return client
        }

        val journalforingService = JournalforingService(
            dokarkivClient = externalMockEnvironment.dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
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
        val svarfrist = LocalDate.now().plusDays(30)
        val forhandsvarselRequestDTO = VurderingRequestDTO(
            type = VurderingType.FORHANDSVARSEL,
            begrunnelse = begrunnelse,
            document = forhandsvarselDocument,
            frist = svarfrist,
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
            document: List<DocumentComponent>,
            svarfrist: LocalDate? = null,
        ) = vurderingService.createVurdering(
            personident = personident,
            veilederident = veilederident,
            type = type,
            arsak = null,
            begrunnelse = begrunnelse,
            document = document,
            gjelderFom = null,
            callId = UUID.randomUUID().toString(),
            svarfrist = svarfrist,
        )

        suspend fun HttpClient.postVurdering(vurderingRequest: VurderingRequestDTO): HttpResponse =
            this.post(urlVurdering) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, personIdent)
                setBody(vurderingRequest)
            }

        beforeEachTest {
            database.dropData()
        }

        describe("Vurdering") {
            describe("Happy path") {
                it("Successfully creates a new forhandsvarsel with varsel and pdf") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(forhandsvarselRequestDTO)

                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val responseDTO = response.body<VurderingResponseDTO>()
                        responseDTO.begrunnelse shouldBeEqualTo begrunnelse
                        responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                        responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                        responseDTO.document shouldBeEqualTo forhandsvarselDocument
                        responseDTO.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                        responseDTO.gjelderFom.shouldBeNull()
                        responseDTO.varsel shouldNotBeEqualTo null
                        responseDTO.varsel!!.svarfrist shouldBeEqualTo svarfrist

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
                            document = forhandsvarselDocument,
                            svarfrist = LocalDate.now().plusDays(21),
                        )
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(forhandsvarselRequestDTO)

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("Creates new vurdering OPPFYLT and creates PDF") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(vurderingRequestDTO)

                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val responseDTO = response.body<VurderingResponseDTO>()
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
                it("Creates new vurdering OPPFYLT_UTEN_FORHANDSVARSEL and creates PDF") {
                    testApplication {
                        val request = VurderingRequestDTO(
                            type = VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL,
                            begrunnelse = begrunnelse,
                            document = vurderingDocumentOppfylt,
                            arsak = VurderingArsak.NAY_BER_OM_NY_VURDERING,
                        )

                        val client = setupApiAndClient()
                        val response = client.postVurdering(request)

                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val responseDTO = response.body<VurderingResponseDTO>()
                        responseDTO.begrunnelse shouldBeEqualTo begrunnelse
                        responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                        responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                        responseDTO.document shouldBeEqualTo vurderingDocumentOppfylt
                        responseDTO.type shouldBeEqualTo VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL
                        responseDTO.arsak shouldBeEqualTo VurderingArsak.NAY_BER_OM_NY_VURDERING
                        responseDTO.gjelderFom.shouldBeNull()
                        responseDTO.varsel shouldBeEqualTo null

                        val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
                        vurdering.begrunnelse shouldBeEqualTo begrunnelse
                        vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                        vurdering.type shouldBeEqualTo VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL
                        vurdering.gjelderFom.shouldBeNull()
                        vurdering.varsel shouldBeEqualTo null
                        vurdering.arsak() shouldBeEqualTo VurderingArsak.NAY_BER_OM_NY_VURDERING.name

                        val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
                        pVurderingPdf?.pdf?.size shouldBeEqualTo PDF_VURDERING.size
                        pVurderingPdf?.pdf?.get(0) shouldBeEqualTo PDF_VURDERING[0]
                        pVurderingPdf?.pdf?.get(1) shouldBeEqualTo PDF_VURDERING[1]
                    }
                }
                it("Creates new vurdering AVSLAG and creates PDF") {
                    val expiredForhandsvarsel =
                        generateForhandsvarselVurdering(svarfrist = LocalDate.now().minusDays(1))
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
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(vurderingAvslagRequestDTO)

                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val responseDTO = response.body<VurderingResponseDTO>()
                        responseDTO.begrunnelse shouldBeEqualTo "Avslag"
                        responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                        responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                        responseDTO.document shouldBeEqualTo vurderingDocumentAvslag
                        responseDTO.type shouldBeEqualTo VurderingType.AVSLAG
                        responseDTO.arsak.shouldBeNull()
                        responseDTO.gjelderFom shouldBeEqualTo avslagGjelderFom
                        responseDTO.varsel shouldBeEqualTo null
                        responseDTO.oppgaveFraNayDato shouldBeEqualTo null

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

                it("Creates new vurdering AVSLAG_UTEN_FORHANDSVARSEL and creates PDF") {
                    val avslagGjelderFom = LocalDate.now().plusDays(1)
                    val oppgaveDato = LocalDate.now().minusDays(1)
                    val vurderingAvslagRequestDTO = VurderingRequestDTO(
                        type = VurderingType.AVSLAG_UTEN_FORHANDSVARSEL,
                        begrunnelse = "Avslag",
                        document = vurderingDocumentAvslag,
                        gjelderFom = avslagGjelderFom,
                        arsak = VurderingArsak.SYKEPENGER_IKKE_UTBETALT,
                        oppgaveFraNayDato = oppgaveDato,
                    )
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(vurderingAvslagRequestDTO)

                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val responseDTO = response.body<VurderingResponseDTO>()
                        responseDTO.begrunnelse shouldBeEqualTo "Avslag"
                        responseDTO.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT.value
                        responseDTO.veilederident shouldBeEqualTo VEILEDER_IDENT
                        responseDTO.document shouldBeEqualTo vurderingDocumentAvslag
                        responseDTO.type shouldBeEqualTo VurderingType.AVSLAG_UTEN_FORHANDSVARSEL
                        responseDTO.arsak shouldBeEqualTo VurderingArsak.SYKEPENGER_IKKE_UTBETALT
                        responseDTO.gjelderFom shouldBeEqualTo avslagGjelderFom
                        responseDTO.varsel shouldBeEqualTo null
                        responseDTO.oppgaveFraNayDato shouldBeEqualTo oppgaveDato

                        val vurderinger = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
                        vurderinger.size shouldBeEqualTo 1
                        val avslagVurdering = vurderinger.first()
                        avslagVurdering.begrunnelse shouldBeEqualTo "Avslag"
                        avslagVurdering.document shouldBeEqualTo vurderingDocumentAvslag
                        avslagVurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                        avslagVurdering.type shouldBeEqualTo VurderingType.AVSLAG_UTEN_FORHANDSVARSEL
                        avslagVurdering.gjelderFom shouldBeEqualTo avslagGjelderFom
                        avslagVurdering.varsel shouldBeEqualTo null
                        avslagVurdering.arsak() shouldBeEqualTo VurderingArsak.SYKEPENGER_IKKE_UTBETALT.name

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
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(ikkeAktuellRequestDTO)

                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val responseDTO = response.body<VurderingResponseDTO>()
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
                            document = forhandsvarselDocument,
                            svarfrist = LocalDate.now().plusDays(21),
                        )
                    }
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOs = response.body<List<VurderingResponseDTO>>()
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
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOs = response.body<List<VurderingResponseDTO>>()
                        responseDTOs.size shouldBeEqualTo 0
                    }
                }
                it("Successfully gets multiple vurderinger") {
                    runBlocking {
                        createVurdering(
                            type = VurderingType.FORHANDSVARSEL,
                            document = forhandsvarselDocument,
                            svarfrist = LocalDate.now().plusDays(21),
                        )
                        createVurdering(type = VurderingType.OPPFYLT, document = generateDocumentComponent("Oppfylt"))
                    }
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOs = response.body<List<VurderingResponseDTO>>()
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
                            svarfrist = if (type == VurderingType.FORHANDSVARSEL) LocalDate.now().plusDays(21) else null,
                        )
                    }
                }

                it("Gets all vurderinger for all persons") {
                    runBlocking { createVurderinger() }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val responseDTO = response.body<VurderingerResponseDTO>()

                        responseDTO.vurderinger.size shouldBeEqualTo 3
                        responseDTO.vurderinger.keys shouldContainAll personidenter.map { it.value }
                        responseDTO.vurderinger.forEach { (_, vurdering) ->
                            vurdering.veilederident shouldBeEqualTo VEILEDER_IDENT
                            vurdering.begrunnelse shouldBeEqualTo begrunnelse
                            vurdering.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                            vurdering.varsel.shouldNotBeNull()
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

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val responseDTO = response.body<VurderingerResponseDTO>()

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

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val responseDTO = response.body<VurderingerResponseDTO>()

                        responseDTO.vurderinger.size shouldBeEqualTo 1
                        responseDTO.vurderinger.keys shouldContain ARBEIDSTAKER_PERSONIDENT.value
                        responseDTO.vurderinger.forEach { (_, vurdering) ->
                            vurdering.veilederident shouldBeEqualTo VEILEDER_IDENT
                            vurdering.begrunnelse shouldBeEqualTo begrunnelse
                        }
                    }
                }

                it("Gets vurderinger only for persons where veileder has access") {
                    val identer = listOf(ARBEIDSTAKER_PERSONIDENT, ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS)
                    runBlocking { createVurderinger(identer = identer) }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(VurderingerRequestDTO(identer.map { it.value }))
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val responseDTO = response.body<VurderingerResponseDTO>()

                        responseDTO.vurderinger.size shouldBeEqualTo 1
                        responseDTO.vurderinger.keys shouldContain ARBEIDSTAKER_PERSONIDENT.value
                        responseDTO.vurderinger.keys shouldNotContain ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value
                    }
                }

                it("Gets no vurderinger when none of the persons has vurdering") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.NoContent
                    }
                }
            }

            describe("Unhappy path") {
                it("Throws error when document is empty") {
                    val vurderingWithoutDocument = vurderingRequestDTO.copy(document = emptyList())
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.postVurdering(vurderingWithoutDocument)

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("Returns status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering)

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
                it("Returns status Forbidden if denied access to person") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
                it("Returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering) {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("Returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(urlVurdering) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value.drop(1))
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
