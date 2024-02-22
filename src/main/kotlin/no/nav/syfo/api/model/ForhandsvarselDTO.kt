package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent

data class ForhandsvarselDTO(
    val begrunnelse: String,
    val document: List<DocumentComponent> = emptyList()
)
