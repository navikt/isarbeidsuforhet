package no.nav.syfo.infrastructure.clients.veiledertilgang

class ForbiddenAccessVeilederException(
    action: String,
    message: String = "Denied NAVIdent access to personIdent: $action"
) : RuntimeException(message)
