package no.nav.syfo.infrastructure.wellknown

data class WellKnown(
    val issuer: String,
    val jwksUri: String
)
