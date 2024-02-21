package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.database.DatabaseInterface

const val podLivenessPath = "/internal/is_alive"
const val podReadinessPath = "/internal/is_ready"

fun Routing.podEndpoints(applicationState: ApplicationState, database: DatabaseInterface) {
    get(podLivenessPath) {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get(podReadinessPath) {
        if (applicationState.ready && database.isOk()) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun DatabaseInterface.isOk(): Boolean {
    return try {
        connection.use {
            it.isValid(1)
        }
    } catch (ex: Exception) {
        false
    }
}
