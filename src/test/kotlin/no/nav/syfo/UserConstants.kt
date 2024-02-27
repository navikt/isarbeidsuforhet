package no.nav.syfo

import no.nav.syfo.domain.PersonIdent

object UserConstants {
    val ARBEIDSTAKER_PERSONIDENT = PersonIdent("12345678910")
    val ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS = PersonIdent("11111111111")
    val ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH = PersonIdent("11111111234")
    val ARBEIDSTAKER_PERSONIDENT_NO_NAME = PersonIdent("11111111222")
    val ARBEIDSTAKER_PERSONIDENT_PDL_FAILS = PersonIdent("11111111666")

    val PDF_FORHANDSVARSEL = byteArrayOf(0x2E, 0x28)
    const val VEILEDER_IDENT = "Z999999"

    const val PERSON_FORNAVN = "Fornavn"
    const val PERSON_MELLOMNAVN = "Mellomnavn"
    const val PERSON_ETTERNAVN = "Etternavnesen"
    const val PERSON_FORNAVN_DASH = "For-Navn"
    const val PERSON_FULLNAME_DASH = "For-Navn Mellomnavn Etternavnesen"
}
