package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.VurderingType

data class VurderingRequestDTO(
    val type: VurderingType,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
)
