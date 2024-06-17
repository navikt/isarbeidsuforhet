package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.veiledertilgang.Tilgang
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_PATH) -> {
            when (request.headers[NAV_PERSONIDENT_HEADER]) {
                ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value -> respond(Tilgang(erGodkjent = false))
                else -> respond(Tilgang(erGodkjent = true))
            }
        }
        requestUrl.endsWith(VeilederTilgangskontrollClient.TILGANGSKONTROLL_BRUKERE_PATH) -> {
            val body = runBlocking<List<String>> { request.receiveBody() }.toMutableList()
            body.removeAll { it == ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value }
            respond(body)
        }
        else -> error("Unhandled path $requestUrl")
    }
}
