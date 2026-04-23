package no.nav.syfo.util

import io.ktor.server.routing.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.clients.veiledertilgang.ForbiddenAccessVeilederException
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient

suspend fun RoutingContext.validateVeilederAccess(
    action: String,
    personidentToAccess: PersonIdent,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    requiresWriteAccess: Boolean = false,
    requestBlock: suspend () -> Unit,
) {
    val callId = getCallId()

    val token = getBearerHeader()
        ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

    val hasVeilederAccess = if (requiresWriteAccess) {
        veilederTilgangskontrollClient.hasWriteAccess(
            callId = callId,
            personident = personidentToAccess,
            token = token,
        )
    } else {
        veilederTilgangskontrollClient.hasAccess(
            callId = callId,
            personident = personidentToAccess,
            token = token,
        )
    }
    if (hasVeilederAccess) {
        requestBlock()
    } else {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    }
}
