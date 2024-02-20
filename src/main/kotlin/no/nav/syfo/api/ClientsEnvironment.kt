package no.nav.syfo.api

data class ClientsEnvironment(
    val istilgangskontroll: ClientEnvironment,
    val isarbeidsuforhetpdfgen: OpenClientEnvironment,
    val pdl: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)

data class OpenClientEnvironment(
    val baseUrl: String,
)
