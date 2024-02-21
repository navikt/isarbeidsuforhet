package no.nav.syfo

import no.nav.syfo.api.*
import no.nav.syfo.infrastructure.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISARBEIDSUFORHET_ISARBEIDSUFORHET_DB"

data class Environment(
    val database: DatabaseEnvironment = DatabaseEnvironment(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
    ),
    val azure: AzureEnvironment =
        AzureEnvironment(
            appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
            appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
            appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
            openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
        ),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val clients: ClientsEnvironment =
        ClientsEnvironment(
            istilgangskontroll = ClientEnvironment(
                baseUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
                clientId = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID")
            ),
            isarbeidsuforhetpdfgen = OpenClientEnvironment(
                baseUrl = "http://isarbeidsuforhetpdfgen"
            ),
            pdl = ClientEnvironment(
                baseUrl = getEnvVar("PDL_URL"),
                clientId = getEnvVar("PDL_CLIENT_ID"),
            ),
        ),
)

fun getEnvVar(
    varName: String,
    defaultValue: String? = null
) = System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal() = getEnvVar("KTOR_ENV", "local") == "local"
