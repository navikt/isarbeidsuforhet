package no.nav.syfo.infrastructure.pdl.dto

data class PdlIdenterDTO(
    val data: PdlHentIdenter?,
    val errors: List<PdlError>?
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?,
)

data class PdlIdenter(
    val identer: List<PdlIdent>,
)

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: IdentType,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}
