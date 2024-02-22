package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.model.ForhandsvarselDTO
import no.nav.syfo.infrastructure.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.veiledertilgang.VeilederTilgangskontrollPlugin
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonIdent

const val arbeidsuforhetApiBasePath = "/api/internad/v1/arbeidsuforhet"
const val forhandsvarselPath = "/forhandsvarsel"

private const val API_ACTION = "access arbeidsuforhet for person"

fun Route.registerArbeidsuforhetEndpoints(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(arbeidsuforhetApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        post(forhandsvarselPath) {
            val requestDTO = call.receive<ForhandsvarselDTO>()
            if (requestDTO.document.isEmpty()) {
                throw IllegalArgumentException("Forhandsvarsel can't have an empty document")
            }

            val personIdent = call.getPersonIdent()
            val navIdent = call.getNAVIdent()
            val callId = call.getCallId()

            call.respond(HttpStatusCode.Created)
        }
    }
}
