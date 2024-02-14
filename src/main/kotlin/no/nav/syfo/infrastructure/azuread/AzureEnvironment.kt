package no.nav.syfo.infrastructure.azuread

data class AzureEnvironment(
    val appClientId: String,
    val appClientSecret: String,
    val appWellKnownUrl: String,
    val openidConfigTokenEndpoint: String
)
