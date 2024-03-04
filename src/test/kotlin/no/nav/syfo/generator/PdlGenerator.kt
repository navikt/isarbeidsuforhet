package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.pdl.dto.*

fun generatePdlPersonResponse(pdlPersonNavn: PdlPersonNavn? = null, errors: List<PdlError>? = null) = PdlPersonResponse(
    errors = errors,
    data = generatePdlHentPerson(pdlPersonNavn)
)

fun generatePdlPersonNavn(): PdlPersonNavn = PdlPersonNavn(
    fornavn = UserConstants.PERSON_FORNAVN,
    mellomnavn = UserConstants.PERSON_MELLOMNAVN,
    etternavn = UserConstants.PERSON_ETTERNAVN,
)

fun generatePdlHentPerson(
    pdlPersonNavn: PdlPersonNavn?,
): PdlHentPerson = PdlHentPerson(
    hentPerson = PdlPerson(
        navn = if (pdlPersonNavn != null) listOf(pdlPersonNavn) else emptyList(),
    )
)

fun generatePdlError() = listOf(
    PdlError(
        message = "Error in PDL",
        locations = emptyList(),
        path = null,
        extensions = PdlErrorExtension(
            code = null,
            classification = "",
        )
    )
)
