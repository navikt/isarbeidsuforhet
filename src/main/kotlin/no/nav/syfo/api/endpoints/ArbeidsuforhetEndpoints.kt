package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.model.VurderingRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollPlugin
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonIdent

const val arbeidsuforhetApiBasePath = "/api/internad/v1/arbeidsuforhet"
const val vurderingPath = "/vurderinger"

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

        post(vurderingPath) {
            val requestDTO = call.receive<VurderingRequestDTO>()
            if (requestDTO.type != VurderingType.AVSLAG && (requestDTO.begrunnelse.isBlank() || requestDTO.document.isEmpty())
            ) {
                throw IllegalArgumentException("Vurdering ${VurderingType.FORHANDSVARSEL} and ${VurderingType.OPPFYLT} can't have an empty begrunnelse or document")
            }

            val personIdent = call.getPersonIdent()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val navIdent = call.getNAVIdent()
            val callId = call.getCallId()

            val existingVurderinger = vurderingService.getVurderinger(personIdent)
            if (existingVurderinger.firstOrNull()?.isForhandsvarsel() == true &&
                requestDTO.type == VurderingType.FORHANDSVARSEL
            ) {
                throw IllegalArgumentException("Duplicate ${VurderingType.FORHANDSVARSEL} for given person")
            }

            val newVurdering = vurderingService.createVurdering(
                personident = personIdent,
                veilederident = navIdent,
                type = requestDTO.type,
                begrunnelse = requestDTO.begrunnelse,
                document = requestDTO.document,
                gjelderFom = requestDTO.gjelderFom,
                callId = callId,
            )

            call.respond(HttpStatusCode.Created, VurderingResponseDTO.createFromVurdering(newVurdering))
        }
    }
}
