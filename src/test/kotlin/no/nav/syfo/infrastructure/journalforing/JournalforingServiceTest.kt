package no.nav.syfo.infrastructure.journalforing

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_AVSLAG
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateJournalpostRequest
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostType
import no.nav.syfo.infrastructure.mock.dokarkivResponse
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JournalforingServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val dokarkivMock = mockk<DokarkivClient>(relaxed = true)
    private val journalforingService = JournalforingService(
        dokarkivClient = dokarkivMock,
        pdlClient = externalMockEnvironment.pdlClient,
        isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
    )

    @BeforeEach
    fun init() {
        clearAllMocks()
        coEvery { dokarkivMock.journalfor(any()) } returns dokarkivResponse
    }

    @Test
    fun `journalfører OPPFYLT vurdering`() = runBlocking {
        val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)
        val journalpostId = journalforingService.journalfor(
            personident = ARBEIDSTAKER_PERSONIDENT,
            pdf = PDF_VURDERING,
            vurdering = vurderingOppfylt,
        )
        assertEquals(mockedJournalpostId, journalpostId)
        coVerify(exactly = 1) {
            dokarkivMock.journalfor(
                journalpostRequest = generateJournalpostRequest(
                    tittel = "Vurdering av § 8-4 arbeidsuførhet",
                    brevkodeType = BrevkodeType.ARBEIDSUFORHET_VURDERING,
                    pdf = PDF_VURDERING,
                    vurderingUuid = vurderingOppfylt.uuid,
                    journalpostType = JournalpostType.UTGAAENDE.name,
                )
            )
        }
    }

    @Test
    fun `journalfører FORHANDSVARSEL vurdering`() = runBlocking {
        val vurderingForhandsvarsel = generateForhandsvarselVurdering()
        val journalpostId = journalforingService.journalfor(
            personident = ARBEIDSTAKER_PERSONIDENT,
            pdf = PDF_FORHANDSVARSEL,
            vurdering = vurderingForhandsvarsel,
        )
        assertEquals(mockedJournalpostId, journalpostId)
        coVerify(exactly = 1) {
            dokarkivMock.journalfor(
                journalpostRequest = generateJournalpostRequest(
                    tittel = "Forhåndsvarsel om avslag på sykepenger",
                    brevkodeType = BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL,
                    pdf = PDF_FORHANDSVARSEL,
                    vurderingUuid = vurderingForhandsvarsel.uuid,
                    journalpostType = JournalpostType.UTGAAENDE.name,
                )
            )
        }
    }

    @Test
    fun `journalfører AVSLAG vurdering`() = runBlocking {
        val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
        val journalpostId = journalforingService.journalfor(
            personident = ARBEIDSTAKER_PERSONIDENT,
            pdf = PDF_AVSLAG,
            vurdering = vurderingAvslag,
        )
        assertEquals(mockedJournalpostId, journalpostId)
        coVerify(exactly = 1) {
            dokarkivMock.journalfor(
                journalpostRequest = generateJournalpostRequest(
                    tittel = "Innstilling om avslag",
                    brevkodeType = BrevkodeType.ARBEIDSUFORHET_AVSLAG,
                    pdf = PDF_AVSLAG,
                    vurderingUuid = vurderingAvslag.uuid,
                    journalpostType = JournalpostType.NOTAT.name,
                )
            )
        }
    }

    @Test
    fun `journalfører IKKE_AKTUELL vurdering`() = runBlocking {
        val vurderingIkkeAktuell = generateVurdering(type = VurderingType.IKKE_AKTUELL)
        val journalpostId = journalforingService.journalfor(
            personident = ARBEIDSTAKER_PERSONIDENT,
            pdf = PDF_VURDERING,
            vurdering = vurderingIkkeAktuell,
        )
        assertEquals(mockedJournalpostId, journalpostId)
        coVerify(exactly = 1) {
            dokarkivMock.journalfor(
                journalpostRequest = generateJournalpostRequest(
                    tittel = "Vurdering av § 8-4 arbeidsuførhet",
                    brevkodeType = BrevkodeType.ARBEIDSUFORHET_VURDERING,
                    pdf = PDF_VURDERING,
                    vurderingUuid = vurderingIkkeAktuell.uuid,
                    journalpostType = JournalpostType.UTGAAENDE.name,
                )
            )
        }
    }
}
