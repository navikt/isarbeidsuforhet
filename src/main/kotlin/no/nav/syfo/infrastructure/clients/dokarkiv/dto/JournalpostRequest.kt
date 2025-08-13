package no.nav.syfo.infrastructure.clients.dokarkiv.dto

const val JOURNALFORENDE_ENHET = 9999

/**
 * UTGAAENDE brukes for dokumentasjon som NAV har produsert og sendt ut til en ekstern part. Dette kan for eksempel være informasjons- eller vedtaksbrev til privatpersoner eller organisasjoner.
 *
 * NOTAT brukes for dokumentasjon som NAV har produsert selv og uten mål om å distribuere dette ut av NAV. Eksempler på dette er forvaltningsnotater og referater fra telefonsamtaler med brukere.
 */
enum class JournalpostType {
    UTGAAENDE,
    NOTAT,
}

enum class JournalpostTema(val value: String) {
    OPPFOLGING("OPP"),
}

data class JournalpostRequest(
    val avsenderMottaker: AvsenderMottaker? = null,
    val tittel: String,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int? = JOURNALFORENDE_ENHET,
    val journalpostType: String,
    val tema: String = JournalpostTema.OPPFOLGING.value,
    val sak: Sak = Sak(),
    val eksternReferanseId: String,
    val overstyrInnsynsregler: String? = null,
)
