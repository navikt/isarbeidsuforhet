package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import no.nav.syfo.infrastructure.clients.azuread.AzureAdTokenResponse

fun MockRequestHandleScope.azureAdMockResponse(request: HttpRequestData): HttpResponseData {
    val assertionToken = (request.body as? FormDataContent)?.formData?.get("assertion")

    return respond(
        AzureAdTokenResponse(
            access_token = assertionToken ?: "token",
            expires_in = 3600,
            token_type = "type",
        )
    )
}
