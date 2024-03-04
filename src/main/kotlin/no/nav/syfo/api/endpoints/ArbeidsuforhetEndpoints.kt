package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.model.ForhandsvarselRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollPlugin
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonIdent

const val arbeidsuforhetApiBasePath = "/api/internad/v1/arbeidsuforhet"
const val forhandsvarselPath = "/forhandsvarsel"
const val vurderingPath = "/vurdering"

private const val API_ACTION = "access arbeidsuforhet for person"

fun Route.registerArbeidsuforhetEndpoints(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    vurderingService: VurderingService,
) {
    route(arbeidsuforhetApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        get(vurderingPath) {
            val personIdent = call.getPersonIdent()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")

            val vurderinger = vurderingService.getVurderinger(
                personident = personIdent,
            )
            val responseDTO = vurderinger.map { vurdering -> VurderingResponseDTO.createFromVurdering(vurdering) }
            call.respond(HttpStatusCode.OK, responseDTO)
        }

        post(forhandsvarselPath) {
            val requestDTO = call.receive<ForhandsvarselRequestDTO>()
            if (requestDTO.document.isEmpty()) {
                throw IllegalArgumentException("Forhandsvarsel can't have an empty document")
            }

            val personIdent = call.getPersonIdent()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val navIdent = call.getNAVIdent()
            val callId = call.getCallId()

            val newForhandsvarselVurdering = vurderingService.createForhandsvarsel(
                personident = personIdent,
                veilederident = navIdent,
                begrunnelse = requestDTO.begrunnelse,
                document = requestDTO.document,
                callId = callId,
            )
            val responseDTO = VurderingResponseDTO.createFromVurdering(newForhandsvarselVurdering)
            call.respond(HttpStatusCode.Created, responseDTO)
        }
    }
}
