package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.model.VurderingRequestDTO
import no.nav.syfo.api.model.VurderingResponseDTO
import no.nav.syfo.api.model.VurderingerRequestDTO
import no.nav.syfo.api.model.VurderingerResponseDTO
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*

const val arbeidsuforhetApiBasePath = "/api/internad/v1/arbeidsuforhet"
const val vurderingPath = "/vurderinger"

private const val API_ACTION = "access arbeidsuforhet for person"

fun Route.registerArbeidsuforhetEndpoints(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    vurderingService: VurderingService,
) {
    route(arbeidsuforhetApiBasePath) {
        get(vurderingPath) {
            val personIdent = call.getPersonIdent()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")

            validateVeilederAccess(
                action = API_ACTION,
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val vurderinger = vurderingService.getVurderinger(
                    personident = personIdent,
                )
                val responseDTO = vurderinger.map { vurdering -> VurderingResponseDTO.createFromVurdering(vurdering) }
                call.respond(HttpStatusCode.OK, responseDTO)
            }
        }

        post(vurderingPath) {
            val personIdent = call.getPersonIdent()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")

            validateVeilederAccess(
                action = API_ACTION,
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val requestDTO = call.receive<VurderingRequestDTO>()
                val callId = call.getCallId()
                if (requestDTO.document.isEmpty()) {
                    throw IllegalArgumentException("Vurdering can't have empty document, callId: $callId")
                }

                val navIdent = call.getNAVIdent()

                val newVurdering = vurderingService.createVurdering(
                    personident = personIdent,
                    veilederident = navIdent,
                    type = requestDTO.type,
                    arsak = requestDTO.arsak,
                    begrunnelse = requestDTO.begrunnelse,
                    document = requestDTO.document,
                    gjelderFom = requestDTO.gjelderFom,
                    svarfrist = requestDTO.frist,
                    nayOppgaveDato = requestDTO.oppgaveFraNayDato,
                    callId = callId,
                )

                call.respond(HttpStatusCode.Created, VurderingResponseDTO.createFromVurdering(newVurdering))
            }
        }
        post("/get-vurderinger") {
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to get vurderinger for personer. No Authorization header supplied.")
            val requestDTOList = call.receive<VurderingerRequestDTO>()

            val personerVeilederHasAccessTo = veilederTilgangskontrollClient.veilederPersonerAccess(
                personidenter = requestDTOList.personidenter.map { PersonIdent(it) },
                token = token,
                callId = call.getCallId(),
            )

            val vurderinger = if (personerVeilederHasAccessTo.isNullOrEmpty()) {
                emptyMap()
            } else {
                vurderingService.getLatestVurderingForPersoner(
                    personidenter = personerVeilederHasAccessTo,
                )
            }

            if (vurderinger.isEmpty()) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                val responseDTO = VurderingerResponseDTO(
                    vurderinger = vurderinger.map {
                        it.key.value to VurderingResponseDTO.createFromVurdering(it.value)
                    }.associate { it },
                )
                call.respond(responseDTO)
            }
        }
    }
}
