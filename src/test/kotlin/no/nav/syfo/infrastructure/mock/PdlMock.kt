package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.generator.generatePdlError
import no.nav.syfo.generator.generatePdlPersonNavn
import no.nav.syfo.generator.generatePdlPersonResponse
import no.nav.syfo.infrastructure.clients.pdl.dto.PdlHentPersonRequest
import no.nav.syfo.infrastructure.clients.pdl.dto.PdlPersonNavn

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentPersonRequest>()
    return when (PersonIdent(pdlRequest.variables.ident)) {
        UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME -> respond(generatePdlPersonResponse(pdlPersonNavn = null))
        UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH -> respond(
            generatePdlPersonResponse(
                PdlPersonNavn(
                    fornavn = UserConstants.PERSON_FORNAVN_DASH,
                    mellomnavn = UserConstants.PERSON_MELLOMNAVN,
                    etternavn = UserConstants.PERSON_ETTERNAVN,
                )
            )
        )
        UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS -> respond(generatePdlPersonResponse(errors = generatePdlError()))
        else -> respond(generatePdlPersonResponse(generatePdlPersonNavn()))
    }
}
