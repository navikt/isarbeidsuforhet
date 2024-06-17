package no.nav.syfo.api.model

data class VurderingerRequestDTO(
    val personidenter: List<String>
)

data class VurderingerResponseDTO(
    val vurderinger: Map<String, VurderingResponseDTO>
)
