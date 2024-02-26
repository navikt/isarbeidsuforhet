package no.nav.syfo.domain.model

import no.nav.syfo.domain.PersonIdent
import java.util.UUID

data class UnpublishedVarsel(
    val personident: PersonIdent,
    val varselUuid: UUID,
    val journalpostId: String,
)
