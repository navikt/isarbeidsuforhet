package no.nav.syfo

import no.nav.syfo.domain.PersonIdent

object UserConstants {
    val ARBEIDSTAKER_PERSONIDENT = PersonIdent("12345678910")
    val ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS = PersonIdent("11111111111")
    val PDF_FORHANDSVARSEL = byteArrayOf(0x2E, 0x28)
    const val VEILEDER_IDENT = "Z999999"
}
