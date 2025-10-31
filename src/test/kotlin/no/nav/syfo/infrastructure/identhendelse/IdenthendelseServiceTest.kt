package no.nav.syfo.infrastructure.identhendelse

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateIdenthendelse
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class IdenthendelseServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vurderingRepository = VurderingRepository(database = database)
    private val identhendelseService = IdenthendelseService(
        vurderingRepository = vurderingRepository,
    )

    private val aktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    private val inaktivIdent = UserConstants.ARBEIDSTAKER_3_PERSONIDENT
    private val annenInaktivIdent = UserConstants.ARBEIDSTAKER_2_PERSONIDENT

    private val vurderingMedInaktivIdent = generateVurdering(personident = inaktivIdent, type = VurderingType.OPPFYLT)
    private val vurderingMedAnnenInaktivIdent =
        generateVurdering(personident = annenInaktivIdent, type = VurderingType.AVSLAG)

    @BeforeEach
    fun setup() {
        database.dropData()
    }

    @Test
    fun `flytter vurdering fra inaktiv ident til ny ident når person får ny ident`() {
        vurderingRepository.createVurdering(
            vurdering = vurderingMedInaktivIdent,
            pdf = UserConstants.PDF_VURDERING,
        )
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = listOf(inaktivIdent),
        )
        identhendelseService.handle(identhendelse)

        assertTrue(vurderingRepository.getVurderinger(personident = inaktivIdent).isEmpty())
        assertTrue(vurderingRepository.getVurderinger(personident = aktivIdent).isNotEmpty())
    }

    @Test
    fun `flytter vurderinger fra inaktive identer når person får ny ident`() {
        vurderingRepository.createVurdering(vurdering = vurderingMedInaktivIdent, pdf = UserConstants.PDF_VURDERING)
        vurderingRepository.createVurdering(
            vurdering = vurderingMedAnnenInaktivIdent,
            pdf = UserConstants.PDF_VURDERING
        )

        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = listOf(inaktivIdent, annenInaktivIdent),
        )
        identhendelseService.handle(identhendelse)

        assertTrue(vurderingRepository.getVurderinger(personident = inaktivIdent).isEmpty())
        assertTrue(vurderingRepository.getVurderinger(personident = annenInaktivIdent).isEmpty())
        assertEquals(2, vurderingRepository.getVurderinger(personident = aktivIdent).size)
    }

    @Test
    fun `oppdaterer ingenting når person får ny ident og uten vurdering på inaktiv ident`() {
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = listOf(inaktivIdent),
        )
        identhendelseService.handle(identhendelse)

        assertTrue(vurderingRepository.getVurderinger(personident = inaktivIdent).isEmpty())
        assertTrue(vurderingRepository.getVurderinger(personident = aktivIdent).isEmpty())
    }

    @Test
    fun `oppdaterer ingenting når person får ny ident uten inaktiv identer`() {
        vurderingRepository.createVurdering(vurdering = vurderingMedInaktivIdent, pdf = UserConstants.PDF_VURDERING)
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = emptyList(),
        )
        identhendelseService.handle(identhendelse)

        assertTrue(vurderingRepository.getVurderinger(personident = inaktivIdent).isNotEmpty())
        assertTrue(vurderingRepository.getVurderinger(personident = aktivIdent).isEmpty())
    }

    @Test
    fun `oppdaterer ingenting når person mangler aktiv ident`() {
        vurderingRepository.createVurdering(vurdering = vurderingMedInaktivIdent, pdf = UserConstants.PDF_VURDERING)
        val identhendelse = generateIdenthendelse(
            aktivIdent = null,
            inaktiveIdenter = listOf(inaktivIdent),
        )
        identhendelseService.handle(identhendelse)

        assertTrue(vurderingRepository.getVurderinger(personident = inaktivIdent).isNotEmpty())
        assertTrue(vurderingRepository.getVurderinger(personident = aktivIdent).isEmpty())
    }
}
