package no.nav.syfo

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

fun main() {

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
    }
    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ).start(wait = true)
}
