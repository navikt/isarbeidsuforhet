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
import no.nav.syfo.api.model.VurderingRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.api.model.VurderingerRequestDTO
import no.nav.syfo.api.model.VurderingerResponseDTO
import no.nav.syfo.api.testApiModule
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.*
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVurderingPdf
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.util.configure
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate
import java.util.*

class ArbeidsuforhetEndpointsTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val journalforingService = JournalforingService(
        dokarkivClient = externalMockEnvironment.dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
        isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
    )
    private val vurderingRepository = VurderingRepository(database)
    private val vurderingService = VurderingService(
        vurderingRepository = vurderingRepository,
        vurderingPdfService = VurderingPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        ),
        journalforingService = journalforingService,
        vurderingProducer = mockk<IVurderingProducer>(),
    )
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = VEILEDER_IDENT,
    )

    private val begrunnelse = "Dette er en begrunnelse for vurdering av 8-4"
    private val forhandsvarselDocument = generateDocumentComponent(
        fritekst = begrunnelse,
        header = "Forhåndsvarsel",
    )
    private val vurderingDocumentOppfylt = generateDocumentComponent(
        fritekst = begrunnelse,
        header = "Oppfylt",
    )
    private val vurderingDocumentAvslag = generateDocumentComponent(
        fritekst = begrunnelse,
        header = "Avslag",
    )
    private val svarfrist = LocalDate.now().plusDays(30)
    private val forhandsvarselRequestDTO = VurderingRequestDTO(
        type = VurderingType.FORHANDSVARSEL,
        begrunnelse = begrunnelse,
        document = forhandsvarselDocument,
        frist = svarfrist,
    )
    private val vurderingRequestDTO = VurderingRequestDTO(
        type = VurderingType.OPPFYLT,
        begrunnelse = begrunnelse,
        document = vurderingDocumentOppfylt,
    )
    private val personIdent = ARBEIDSTAKER_PERSONIDENT.value

    private val urlVurdering = "$arbeidsuforhetApiBasePath/$vurderingPath"

    @BeforeEach
    fun cleanDb() {
        database.dropData()
    }

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(
                externalMockEnvironment = ExternalMockEnvironment.instance,
            )
        }
        return createClient {
            install(ContentNegotiation) { jackson { configure() } }
        }
    }

    private suspend fun createVurdering(
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
        vurderingInitiertAv = null,
    )

    private suspend fun HttpClient.postVurdering(vurderingRequest: VurderingRequestDTO): HttpResponse =
        this.post(urlVurdering) {
            bearerAuth(validToken)
            contentType(ContentType.Application.Json)
            header(NAV_PERSONIDENT_HEADER, personIdent)
            setBody(vurderingRequest)
        }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        @Test
        fun `Successfully creates a new forhandsvarsel with varsel and pdf`() = testApplication {
            val client = setupApiAndClient()
            val response = runBlocking { client.postVurdering(forhandsvarselRequestDTO) }

            assertEquals(HttpStatusCode.Created, response.status)

            val responseDTO = runBlocking { response.body<VurderingResponseDTO>() }
            assertEquals(begrunnelse, responseDTO.begrunnelse)
            assertEquals(ARBEIDSTAKER_PERSONIDENT.value, responseDTO.personident)
            assertEquals(VEILEDER_IDENT, responseDTO.veilederident)
            assertEquals(forhandsvarselDocument, responseDTO.document)
            assertEquals(VurderingType.FORHANDSVARSEL, responseDTO.type)
            assertNull(responseDTO.gjelderFom)
            assertNotNull(responseDTO.varsel)
            assertEquals(svarfrist, responseDTO.varsel.svarfrist)

            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
            assertEquals(begrunnelse, vurdering.begrunnelse)
            assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
            assertEquals(VurderingType.FORHANDSVARSEL, vurdering.type)
            assertNotNull((vurdering as Vurdering.Forhandsvarsel).varsel)

            val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
            assertEquals(PDF_FORHANDSVARSEL.size, pVurderingPdf?.pdf?.size)
            assertEquals(PDF_FORHANDSVARSEL[0], pVurderingPdf?.pdf?.get(0))
            assertEquals(PDF_FORHANDSVARSEL[1], pVurderingPdf?.pdf?.get(1))
        }

        @Test
        fun `Validates too short svarfrist when create a new forhandsvarsel`() = testApplication {
            val client = setupApiAndClient()
            val response = runBlocking {
                client.postVurdering(
                    forhandsvarselRequestDTO.copy(
                        frist = LocalDate.now().plusDays(20),
                    )
                )
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `Validates too long svarfrist when create a new forhandsvarsel`() = testApplication {
            val client = setupApiAndClient()
            val response = runBlocking {
                client.postVurdering(
                    forhandsvarselRequestDTO.copy(
                        frist = LocalDate.now().plusDays(43),
                    )
                )
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `Validates missing svarfrist when create a new forhandsvarsel`() = testApplication {
            val client = setupApiAndClient()
            val response = runBlocking {
                client.postVurdering(
                    forhandsvarselRequestDTO.copy(
                        frist = null,
                    )
                )
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `Does not allow duplicate forhandsvarsel`() {
            runBlocking {
                createVurdering(
                    type = VurderingType.FORHANDSVARSEL,
                    document = forhandsvarselDocument,
                    svarfrist = LocalDate.now().plusDays(21),
                )
            }
            testApplication {
                val client = setupApiAndClient()
                val response = runBlocking { client.postVurdering(forhandsvarselRequestDTO) }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

        @Test
        fun `Creates new vurdering OPPFYLT and creates PDF`() = testApplication {
            val client = setupApiAndClient()
            val response = runBlocking { client.postVurdering(vurderingRequestDTO) }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseDTO = runBlocking { response.body<VurderingResponseDTO>() }
            assertEquals(begrunnelse, responseDTO.begrunnelse)
            assertEquals(ARBEIDSTAKER_PERSONIDENT.value, responseDTO.personident)
            assertEquals(VEILEDER_IDENT, responseDTO.veilederident)
            assertEquals(vurderingDocumentOppfylt, responseDTO.document)
            assertEquals(VurderingType.OPPFYLT, responseDTO.type)
            assertNull(responseDTO.arsak)
            assertNull(responseDTO.gjelderFom)
            assertNull(responseDTO.varsel)

            val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
            assertEquals(begrunnelse, vurdering.begrunnelse)
            assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
            assertEquals(VurderingType.OPPFYLT, vurdering.type)

            val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
            assertEquals(PDF_VURDERING.size, pVurderingPdf?.pdf?.size)
            assertEquals(PDF_VURDERING[0], pVurderingPdf?.pdf?.get(0))
            assertEquals(PDF_VURDERING[1], pVurderingPdf?.pdf?.get(1))
        }

        @Test
        fun `Creates new vurdering AVSLAG and creates PDF`() {
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
                gjelderFom = avslagGjelderFom,
            )
            testApplication {
                val client = setupApiAndClient()
                val response = runBlocking { client.postVurdering(vurderingAvslagRequestDTO) }

                assertEquals(HttpStatusCode.Created, response.status)
                val responseDTO = runBlocking { response.body<VurderingResponseDTO>() }
                assertEquals("Avslag", responseDTO.begrunnelse)
                assertEquals(ARBEIDSTAKER_PERSONIDENT.value, responseDTO.personident)
                assertEquals(VEILEDER_IDENT, responseDTO.veilederident)
                assertEquals(vurderingDocumentAvslag, responseDTO.document)
                assertEquals(VurderingType.AVSLAG, responseDTO.type)
                assertNull(responseDTO.arsak)
                assertEquals(avslagGjelderFom, responseDTO.gjelderFom)
                assertNull(responseDTO.varsel)
                assertNull(responseDTO.oppgaveFraNayDato)

                val vurderinger = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
                assertEquals(2, vurderinger.size)
                val avslagVurdering = vurderinger.first()
                assertEquals("Avslag", avslagVurdering.begrunnelse)
                assertEquals(vurderingDocumentAvslag, avslagVurdering.document)
                assertEquals(ARBEIDSTAKER_PERSONIDENT, avslagVurdering.personident)
                assertEquals(VurderingType.AVSLAG, avslagVurdering.type)
                assertEquals(avslagGjelderFom, (avslagVurdering as Vurdering.Avslag).gjelderFom)

                val pVurderingPdf = database.getVurderingPdf(avslagVurdering.uuid)
                assertEquals(PDF_AVSLAG.size, pVurderingPdf?.pdf?.size)
                assertEquals(PDF_AVSLAG[0], pVurderingPdf?.pdf?.get(0))
                assertEquals(PDF_AVSLAG[1], pVurderingPdf?.pdf?.get(1))
            }
        }

        @Test
        fun `Creates new vurdering AVSLAG_UTEN_FORHANDSVARSEL and creates PDF`() {
            val avslagGjelderFom = LocalDate.now().plusDays(1)
            val oppgaveDato = LocalDate.now().minusDays(1)
            val vurderingAvslagRequestDTO = VurderingRequestDTO(
                type = VurderingType.AVSLAG_UTEN_FORHANDSVARSEL,
                begrunnelse = "Avslag",
                document = vurderingDocumentAvslag,
                gjelderFom = avslagGjelderFom,
                vurderingInitiertAv = Vurdering.AvslagUtenForhandsvarsel.VurderingInitiertAv.NAV_KONTOR,
                oppgaveFraNayDato = oppgaveDato,
            )
            testApplication {
                val client = setupApiAndClient()
                val response = runBlocking { client.postVurdering(vurderingAvslagRequestDTO) }

                assertEquals(HttpStatusCode.Created, response.status)
                val responseDTO = runBlocking { response.body<VurderingResponseDTO>() }
                assertEquals("Avslag", responseDTO.begrunnelse)
                assertEquals(ARBEIDSTAKER_PERSONIDENT.value, responseDTO.personident)
                assertEquals(VEILEDER_IDENT, responseDTO.veilederident)
                assertEquals(vurderingDocumentAvslag, responseDTO.document)
                assertEquals(VurderingType.AVSLAG_UTEN_FORHANDSVARSEL, responseDTO.type)
                assertEquals(avslagGjelderFom, responseDTO.gjelderFom)
                assertNull(responseDTO.varsel)
                assertEquals(
                    Vurdering.AvslagUtenForhandsvarsel.VurderingInitiertAv.NAV_KONTOR,
                    responseDTO.vurderingInitiertAv,
                )
                assertEquals(oppgaveDato, responseDTO.oppgaveFraNayDato)

                val vurderinger = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
                assertEquals(1, vurderinger.size)
                val avslagVurdering = vurderinger.first()
                assertEquals("Avslag", avslagVurdering.begrunnelse)
                assertEquals(vurderingDocumentAvslag, avslagVurdering.document)
                assertEquals(ARBEIDSTAKER_PERSONIDENT, avslagVurdering.personident)
                assertEquals(VurderingType.AVSLAG_UTEN_FORHANDSVARSEL, avslagVurdering.type)
                assertEquals(
                    avslagGjelderFom,
                    (avslagVurdering as Vurdering.AvslagUtenForhandsvarsel).gjelderFom
                )
                assertEquals(
                    Vurdering.AvslagUtenForhandsvarsel.VurderingInitiertAv.NAV_KONTOR,
                    avslagVurdering.vurderingInitiertAv,
                )

                val pVurderingPdf = database.getVurderingPdf(avslagVurdering.uuid)
                assertEquals(PDF_AVSLAG.size, pVurderingPdf?.pdf?.size)
                assertEquals(PDF_AVSLAG[0], pVurderingPdf?.pdf?.get(0))
                assertEquals(PDF_AVSLAG[1], pVurderingPdf?.pdf?.get(1))
            }
        }

        @Test
        fun `Creates new vurdering IKKE_AKTUELL and creates PDF`() {
            val ikkeAktuellRequestDTO = VurderingRequestDTO(
                type = VurderingType.IKKE_AKTUELL,
                begrunnelse = "",
                document = generateDocumentComponent(fritekst = ""),
                arsak = VurderingArsak.FRISKMELDT,
            )
            testApplication {
                val client = setupApiAndClient()
                val response = runBlocking { client.postVurdering(ikkeAktuellRequestDTO) }

                assertEquals(HttpStatusCode.Created, response.status)
                val responseDTO = runBlocking { response.body<VurderingResponseDTO>() }
                assertEquals(ARBEIDSTAKER_PERSONIDENT.value, responseDTO.personident)
                assertEquals(VEILEDER_IDENT, responseDTO.veilederident)
                assertEquals(VurderingType.IKKE_AKTUELL, responseDTO.type)
                assertEquals(VurderingArsak.FRISKMELDT, responseDTO.arsak)
                assertTrue(responseDTO.document.isNotEmpty())
                assertTrue(responseDTO.begrunnelse.isEmpty())
                assertNull(responseDTO.gjelderFom)
                assertNull(responseDTO.varsel)

                val vurdering = vurderingRepository.getVurderinger(ARBEIDSTAKER_PERSONIDENT).single()
                assertTrue(vurdering.begrunnelse.isEmpty())
                assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
                assertEquals(VurderingType.IKKE_AKTUELL, vurdering.type)

                val pVurderingPdf = database.getVurderingPdf(vurdering.uuid)
                assertEquals(PDF_VURDERING.size, pVurderingPdf?.pdf?.size)
                assertEquals(PDF_VURDERING[0], pVurderingPdf?.pdf?.get(0))
                assertEquals(PDF_VURDERING[1], pVurderingPdf?.pdf?.get(1))
            }
        }

        @Test
        fun `Successfully gets an existing vurdering`() {
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

                assertEquals(HttpStatusCode.OK, response.status)
                val responseDTOs = runBlocking { response.body<List<VurderingResponseDTO>>() }
                assertEquals(1, responseDTOs.size)

                val responseDTO = responseDTOs.first()
                assertEquals(begrunnelse, responseDTO.begrunnelse)
                assertEquals(ARBEIDSTAKER_PERSONIDENT.value, responseDTO.personident)
                assertEquals(VEILEDER_IDENT, responseDTO.veilederident)
                assertEquals(forhandsvarselDocument, responseDTO.document)
                assertEquals(VurderingType.FORHANDSVARSEL, responseDTO.type)
                assertNull(responseDTO.arsak)
                assertNotNull(responseDTO.varsel)
                assertEquals(LocalDate.now().plusWeeks(3), responseDTO.varsel?.svarfrist)
                assertFalse(responseDTO.varsel.isExpired)
            }
        }

        @Test
        fun `Successfully gets empty list of vurderinger`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVurdering) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personIdent)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val responseDTOs = runBlocking { response.body<List<VurderingResponseDTO>>() }
            assertEquals(0, responseDTOs.size)
        }

        @Test
        fun `Successfully gets multiple vurderinger`() {
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
                assertEquals(HttpStatusCode.OK, response.status)
                val responseDTOs = response.body<List<VurderingResponseDTO>>()
                assertEquals(2, responseDTOs.size)
            }
        }
    }

    @Nested
    @DisplayName("Get vurderinger")
    inner class GetVurderinger {
        private val personidenter =
            listOf(ARBEIDSTAKER_PERSONIDENT, ARBEIDSTAKER_2_PERSONIDENT, ARBEIDSTAKER_3_PERSONIDENT)
        private val requestDTO = VurderingerRequestDTO(personidenter.map { it.value })
        private val url = "$arbeidsuforhetApiBasePath/get-vurderinger"

        private suspend fun createVurderinger(
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

        @Test
        fun `Gets all vurderinger for all persons`() {
            runBlocking { createVurderinger() }
            testApplication {
                val client = setupApiAndClient()
                val response = client.post(url) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(requestDTO)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val responseDTO = runBlocking { response.body<VurderingerResponseDTO>() }

                assertEquals(3, responseDTO.vurderinger.size)
                assertTrue(responseDTO.vurderinger.keys.containsAll(personidenter.map { it.value }))
                responseDTO.vurderinger.forEach { (_, vurdering) ->
                    assertEquals(VEILEDER_IDENT, vurdering.veilederident)
                    assertEquals(begrunnelse, vurdering.begrunnelse)
                    assertEquals(VurderingType.FORHANDSVARSEL, vurdering.type)
                    assertNotNull(vurdering.varsel)
                }
            }
        }

        @Test
        fun `Gets latest vurderinger for all persons`() {
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
                assertEquals(HttpStatusCode.OK, response.status)
                val responseDTO = runBlocking { response.body<VurderingerResponseDTO>() }

                assertEquals(3, responseDTO.vurderinger.size)
                assertTrue(responseDTO.vurderinger.keys.containsAll(personidenter.map { it.value }))
                responseDTO.vurderinger.forEach { (_, vurdering) ->
                    assertEquals(VEILEDER_IDENT, vurdering.veilederident)
                    assertEquals(begrunnelse, vurdering.begrunnelse)
                    if (vurdering.personident == ARBEIDSTAKER_PERSONIDENT.value) {
                        assertEquals(VurderingType.OPPFYLT, vurdering.type)
                        assertNull(vurdering.varsel)
                    } else {
                        assertEquals(VurderingType.FORHANDSVARSEL, vurdering.type)
                        assertNotNull(vurdering.varsel)
                    }
                }
            }
        }

        @Test
        fun `Gets vurderinger only for person with vurdering, even when veileder has access to all persons`() {
            runBlocking { createVurderinger(identer = listOf(ARBEIDSTAKER_PERSONIDENT)) }
            testApplication {
                val client = setupApiAndClient()
                val response = client.post(url) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(requestDTO)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val responseDTO = runBlocking { response.body<VurderingerResponseDTO>() }

                assertEquals(1, responseDTO.vurderinger.size)
                assertTrue(responseDTO.vurderinger.keys.contains(ARBEIDSTAKER_PERSONIDENT.value))
                responseDTO.vurderinger.forEach { (_, vurdering) ->
                    assertEquals(VEILEDER_IDENT, vurdering.veilederident)
                    assertEquals(begrunnelse, vurdering.begrunnelse)
                }
            }
        }

        @Test
        fun `Gets vurderinger only for persons where veileder has access`() {
            val identer = listOf(ARBEIDSTAKER_PERSONIDENT, ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS)
            runBlocking { createVurderinger(identer = identer) }
            testApplication {
                val client = setupApiAndClient()
                val response = client.post(url) {
                    bearerAuth(validToken)
                    contentType(ContentType.Application.Json)
                    setBody(VurderingerRequestDTO(identer.map { it.value }))
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val responseDTO = runBlocking { response.body<VurderingerResponseDTO>() }

                assertEquals(1, responseDTO.vurderinger.size)
                assertTrue(responseDTO.vurderinger.keys.contains(ARBEIDSTAKER_PERSONIDENT.value))
                assertFalse(
                    responseDTO.vurderinger.keys.contains(
                        ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value
                    )
                )
            }
        }

        @Test
        fun `Gets no vurderinger when none of the persons has vurdering`() = testApplication {
            val client = setupApiAndClient()
            val response = client.post(url) {
                bearerAuth(validToken)
                contentType(ContentType.Application.Json)
                setBody(requestDTO)
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {
        @Test
        fun `Throws error when document is empty`() = testApplication {
            val vurderingWithoutDocument = vurderingRequestDTO.copy(document = emptyList())
            val client = setupApiAndClient()
            val response = runBlocking { client.postVurdering(vurderingWithoutDocument) }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `Returns status Unauthorized if no token is supplied`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVurdering)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

        @Test
        fun `Returns status Forbidden if denied access to person`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVurdering) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value)
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

        @Test
        fun `Returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVurdering) { bearerAuth(validToken) }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `Returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() =
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(urlVurdering) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value.drop(1))
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
    }
}
