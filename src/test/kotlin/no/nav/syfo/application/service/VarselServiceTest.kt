package no.nav.syfo.application.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.IDokdistClient
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Varsel
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VarselRepository
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull

private val journalpostId = JournalpostId("123")

class VarselServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val varselRepository = VarselRepository(database = database)
    private val vurderingRepository = VurderingRepository(database = database)
    private val dokdistClient = mockk<IDokdistClient>()
    private val varselService = VarselService(
        varselRepository = varselRepository,
        dokdistClient = dokdistClient,
    )

    private val vurderingForhandsvarsel = generateForhandsvarselVurdering()

    @BeforeEach
    fun setup() {
        clearAllMocks()
        coEvery { dokdistClient.distribuer(journalpostId) } returns "bestillingsId"
    }

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    private fun createUnpublishedVarsel(): Varsel {
        vurderingRepository.createVurdering(
            pdf = UserConstants.PDF_FORHANDSVARSEL,
            vurdering = vurderingForhandsvarsel,
        )
        val unpublishedVarsel = vurderingForhandsvarsel.varsel
        vurderingRepository.setJournalpostId(vurderingForhandsvarsel.copy(journalpostId = journalpostId))
        return unpublishedVarsel
    }

    @Test
    fun `distributes unpublished varsel only once`() = runBlocking {
        val unpublishedVarsel = createUnpublishedVarsel()

        val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
        assertEquals(0, failed.size)
        assertEquals(1, success.size)

        coVerify(exactly = 1) { dokdistClient.distribuer(journalpostId) }

        val publishedVarsel = success.first().getOrThrow()
        assertEquals(unpublishedVarsel.uuid, publishedVarsel.uuid)
        assertNotNull(publishedVarsel.publishedAt)

        assertTrue(varselRepository.getUnpublishedVarsler().isEmpty())

        assertTrue(varselService.publishUnpublishedVarsler().isEmpty())
        coVerify(exactly = 1) { dokdistClient.distribuer(journalpostId) }
    }

    @Test
    fun `distributes nothing when no unpublished varsel`() = runBlocking {
        val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
        assertEquals(0, failed.size)
        assertEquals(0, success.size)
        coVerify(exactly = 0) { dokdistClient.distribuer(journalpostId) }
    }

    @Test
    fun `retries distribution when dokdist fails`() = runBlocking {
        val unpublishedVarsel = createUnpublishedVarsel()
        coEvery { dokdistClient.distribuer(journalpostId) } throws Exception("Error calling dokdist")

        val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
        assertEquals(1, failed.size)
        assertEquals(0, success.size)
        coVerify(exactly = 1) { dokdistClient.distribuer(journalpostId) }

        val (_, _, varsel) = varselRepository.getUnpublishedVarsler().first()
        assertEquals(unpublishedVarsel.uuid, varsel.uuid)

        coEvery { dokdistClient.distribuer(journalpostId) } returns "bestillingsId"
        assertTrue(varselService.publishUnpublishedVarsler().single().isSuccess)
        assertTrue(varselRepository.getUnpublishedVarsler().isEmpty())
    }

    @Test
    fun `does not distribute varsel before journalforing`() = runBlocking {
        vurderingRepository.createVurdering(
            pdf = UserConstants.PDF_FORHANDSVARSEL,
            vurdering = vurderingForhandsvarsel,
        )

        assertTrue(varselService.publishUnpublishedVarsler().isEmpty())
        coVerify(exactly = 0) { dokdistClient.distribuer(journalpostId) }
    }
}
