package no.nav.syfo.infrastructure.clients.pdl.dto

data class PdlHentPersonRequest(
    val query: String,
    val variables: PdlHentPersonRequestVariables
)

data class PdlHentPersonRequestVariables(
    val ident: String,
    val navnHistorikk: Boolean = false
)
