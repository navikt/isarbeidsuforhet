package no.nav.syfo.util

import io.ktor.server.routing.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.clients.veiledertilgang.ForbiddenAccessVeilederException
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient

suspend fun RoutingContext.validateVeilederAccess(
    action: String,
    personIdentToAccess: PersonIdent,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    requestBlock: suspend () -> Unit,
) {
    val callId = getCallId()

    val token = getBearerHeader()
        ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

    val hasVeilederAccess = veilederTilgangskontrollClient.hasAccess(
        callId = callId,
        personIdent = personIdentToAccess,
        token = token,
    )
    if (hasVeilederAccess) {
        requestBlock()
    } else {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    }
}
