package no.nav.syfo.domain

import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateVurdering
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VurderingTest {
    @Test
    fun `return false when forhandsvarsel with svarfrist tomorrow`() {
        val forhandsvarsel = generateForhandsvarselVurdering(svarfrist = LocalDate.now().plusDays(1))
        assertFalse(forhandsvarsel.isExpiredForhandsvarsel())
    }

    @Test
    fun `return false when forhandsvarsel with svarfrist today`() {
        val forhandsvarsel = generateForhandsvarselVurdering(svarfrist = LocalDate.now())
        assertFalse(forhandsvarsel.isExpiredForhandsvarsel())
    }

    @Test
    fun `return true when forhandsvarsel with svarfrist yesterday`() {
        val forhandsvarsel = generateForhandsvarselVurdering(svarfrist = LocalDate.now().minusDays(1))
        assertTrue(forhandsvarsel.isExpiredForhandsvarsel())
    }

    @Test
    fun `returns false when not forhandsvarsel`() {
        val vurdering = generateVurdering(type = VurderingType.OPPFYLT)
        assertFalse(vurdering.isExpiredForhandsvarsel())
    }
}
