package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VurderingRepositoryTest {

    private val database = ExternalMockEnvironment.instance.database
    private val vurderingRepository = VurderingRepository(database = database)

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    @Nested
    @DisplayName("Create forhandsvarsel Vurdering")
    inner class CreateForhandsvarsel {
        private val vurderingForhandsvarsel = generateForhandsvarselVurdering()

        @Test
        fun `creates vurdering, varsel and pdf in database`() {
            vurderingRepository.createVurdering(
                pdf = PDF_FORHANDSVARSEL,
                vurdering = vurderingForhandsvarsel,
            )

            val vurdering = vurderingRepository.getVurderinger(vurderingForhandsvarsel.personident).firstOrNull()
            assertNotNull(vurdering)
            assertEquals(VurderingType.FORHANDSVARSEL, vurdering?.type)
            assertNull(vurdering?.journalpostId)
            val forhandsvarsel = vurdering as Vurdering.Forhandsvarsel
            assertEquals(vurderingForhandsvarsel.varsel.uuid, forhandsvarsel.varsel.uuid)

            val pdf = database.getVurderingPdf(vurdering.uuid)?.pdf
            assertNotNull(pdf)
            assertTrue(pdf!!.isNotEmpty())
            assertEquals(PDF_FORHANDSVARSEL[0], pdf[0])
            assertEquals(PDF_FORHANDSVARSEL[1], pdf[1])
        }
    }

    @Nested
    @DisplayName("Create vurdering variants")
    inner class CreateVurderingVariants {
        private val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)

        @Test
        fun `Creates vurdering OPPFYLT and pdf without varsel`() {
            vurderingRepository.createVurdering(
                vurdering = vurderingOppfylt,
                pdf = PDF_VURDERING,
            )

            val vurdering = vurderingRepository.getVurderinger(vurderingOppfylt.personident).firstOrNull()
            assertNotNull(vurdering)
            assertEquals(VurderingType.OPPFYLT, vurdering!!.type)
            assertNull(vurdering.journalpostId)

            val pdf = database.getVurderingPdf(vurdering.uuid)?.pdf
            assertNotNull(pdf)
            assertEquals(PDF_VURDERING[0], pdf!![0])
            assertEquals(PDF_VURDERING[1], pdf[1])
        }

        @Test
        fun `Creates vurdering AVSLAG and pdf without varsel`() {
            val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
            vurderingRepository.createVurdering(
                vurdering = vurderingAvslag,
                pdf = PDF_VURDERING,
            )

            val vurdering = vurderingRepository.getVurderinger(vurderingAvslag.personident).firstOrNull()
            assertNotNull(vurdering)
            assertEquals(VurderingType.AVSLAG, vurdering!!.type)
            assertNull(vurdering.journalpostId)

            val pdf = database.getVurderingPdf(vurdering.uuid)?.pdf
            assertNotNull(pdf)
            assertEquals(PDF_VURDERING[0], pdf!![0])
            assertEquals(PDF_VURDERING[1], pdf[1])
        }

        @Test
        fun `Creates vurdering IKKE_AKTUELL and pdf without varsel`() {
            val vurderingIkkeAktuell = generateVurdering(type = VurderingType.IKKE_AKTUELL)
            vurderingRepository.createVurdering(
                vurdering = vurderingIkkeAktuell,
                pdf = PDF_VURDERING,
            )

            val vurdering = vurderingRepository.getVurderinger(vurderingIkkeAktuell.personident).firstOrNull()
            assertNotNull(vurdering)
            assertEquals(VurderingType.IKKE_AKTUELL, vurdering!!.type)
            assertNull(vurdering.journalpostId)

            val pdf = database.getVurderingPdf(vurdering.uuid)?.pdf
            assertNotNull(pdf)
            assertEquals(PDF_VURDERING[0], pdf!![0])
            assertEquals(PDF_VURDERING[1], pdf[1])
        }
    }
}
