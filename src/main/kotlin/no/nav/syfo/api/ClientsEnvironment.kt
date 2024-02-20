package no.nav.syfo.api

data class ClientsEnvironment(
    val istilgangskontroll: ClientEnvironment
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String
)
