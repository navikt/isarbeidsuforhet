package no.nav.syfo.web.auth

import no.nav.syfo.infrastructure.wellknown.WellKnown

data class JwtIssuer(
    val acceptedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown
)

enum class JwtIssuerType {
    INTERNAL_AZUREAD
}
