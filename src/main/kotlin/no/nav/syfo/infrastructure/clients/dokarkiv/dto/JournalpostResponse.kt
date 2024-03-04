package no.nav.syfo.infrastructure.clients.dokarkiv.dto

data class JournalpostResponse(
    val dokumenter: List<DokumentInfo>? = null,
    val journalpostId: Int,
    val journalpostferdigstilt: Boolean? = null,
    val journalstatus: String,
    val melding: String? = null,
)
