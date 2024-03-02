package no.nav.syfo.infrastructure.clients.pdl.dto

import java.util.*

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?
)

data class PdlPerson(
    val navn: List<PdlPersonNavn>,
) {
    val fullName: String = navn.firstOrNull()?.fullName()
        ?: throw RuntimeException("PDL returned empty navn for given fnr")
}

data class PdlPersonNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
) {
    fun fullName(): String {
        val fornavn = fornavn.lowerCapitalize()
        val etternavn = etternavn.lowerCapitalize()

        return if (mellomnavn.isNullOrBlank()) {
            "$fornavn $etternavn"
        } else {
            "$fornavn ${mellomnavn.lowerCapitalize()} $etternavn"
        }
    }
}

fun String.lowerCapitalize() =
    this.split(" ").joinToString(" ") { name ->
        val nameWithDash = name.split("-")
        if (nameWithDash.size > 1) {
            nameWithDash.joinToString("-") { it.capitalizeName() }
        } else {
            name.capitalizeName()
        }
    }

private fun String.capitalizeName() =
    this.lowercase(Locale.getDefault()).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
