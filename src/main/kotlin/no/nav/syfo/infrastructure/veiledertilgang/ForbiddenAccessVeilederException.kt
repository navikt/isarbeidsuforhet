package no.nav.syfo.infrastructure.veiledertilgang

class ForbiddenAccessVeilederException(
    action: String,
    message: String = "Denied NAVIdent access to personIdent: $action"
) : RuntimeException(message)
