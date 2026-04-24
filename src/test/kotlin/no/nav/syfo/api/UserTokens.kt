package no.nav.syfo.api

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.UserConstants.VEILEDER_IDENT_NO_WRITE_ACCESS

private val externalMockEnvironment = ExternalMockEnvironment.instance

val tokenForVeilederWithFullTilgang: String = generateJWT(
    audience = externalMockEnvironment.environment.azure.appClientId,
    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
    navIdent = VEILEDER_IDENT,
)

val tokenForVeilederWithNoWriteTilgang: String = generateJWT(
    audience = externalMockEnvironment.environment.azure.appClientId,
    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
    navIdent = VEILEDER_IDENT_NO_WRITE_ACCESS,
)
