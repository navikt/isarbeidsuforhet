package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState

const val podLivenessPath = "/internal/is_alive"
const val podReadinessPath = "/internal/is_ready"

fun Routing.podEndpoints(applicationState: ApplicationState) {
    get(podLivenessPath) {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get(podReadinessPath) {
        if (applicationState.ready) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}
