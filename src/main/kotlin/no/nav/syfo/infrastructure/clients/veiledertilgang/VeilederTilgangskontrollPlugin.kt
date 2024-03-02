package no.nav.syfo.infrastructure.clients.veiledertilgang

import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getPersonIdent

class VeilederTilgangskontrollPluginConfig {
    lateinit var action: String
    lateinit var veilederTilgangskontrollClient: VeilederTilgangskontrollClient
}

val VeilederTilgangskontrollPlugin = createRouteScopedPlugin(
    name = "VeilederTilgangskontrollPlugin",
    createConfiguration = ::VeilederTilgangskontrollPluginConfig
) {
    val action = this.pluginConfig.action
    val veilederTilgangskontrollClient = this.pluginConfig.veilederTilgangskontrollClient

    on(AuthenticationChecked) { call ->
        when {
            call.isHandled -> {
                /** Autentisering kan ha feilet og gitt respons på kallet, ikke gå videre */
            }

            else -> {
                val callId = call.getCallId()
                val personIdent = call.getPersonIdent()
                    ?: throw IllegalArgumentException("Failed to $action: No $NAV_PERSONIDENT_HEADER supplied in request header")
                val token = call.getBearerHeader()
                    ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

                val hasAccess = veilederTilgangskontrollClient.hasAccess(
                    callId = callId,
                    personIdent = personIdent,
                    token = token,
                )
                if (!hasAccess) {
                    throw ForbiddenAccessVeilederException(
                        action = action,
                    )
                }
            }
        }
    }
}
