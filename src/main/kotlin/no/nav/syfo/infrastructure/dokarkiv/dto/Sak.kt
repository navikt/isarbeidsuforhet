package no.nav.syfo.infrastructure.dokarkiv.dto

enum class SaksType(
    val value: String,
) {
    GENERELL("GENERELL_SAK"),
}

data class Sak(
    val sakstype: String = SaksType.GENERELL.value,
)
