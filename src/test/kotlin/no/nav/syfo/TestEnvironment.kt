package no.nav.syfo

import no.nav.syfo.infrastructure.ClientEnvironment
import no.nav.syfo.infrastructure.ClientsEnvironment
import no.nav.syfo.infrastructure.OpenClientEnvironment
import no.nav.syfo.infrastructure.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isaktivitetskrav_dev",
        username = "username",
        password = "password",
    ),
    kafka = KafkaEnvironment(
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
    ),
    azure = AzureEnvironment(
        appClientId = "isarbeidsuforhet-client-id",
        appClientSecret = "isarbeidsuforhet-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    clients = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = "isTilgangskontrollUrl",
            clientId = "dev-gcp.teamsykefravr.istilgangskontroll",
        ),
        pdl = ClientEnvironment(
            baseUrl = "pdlUrl",
            clientId = "pdlClientId",
        ),
        isarbeidsuforhetpdfgen = OpenClientEnvironment(
            baseUrl = "isarbeidsuforhetpdfgenUrl",
        ),
        dokarkiv = ClientEnvironment(
            baseUrl = "dokarkivUrl",
            clientId = "dokarkivClientId",
        ),
    ),
    electorPath = "electorPath",
    publishForhandsvarselEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
