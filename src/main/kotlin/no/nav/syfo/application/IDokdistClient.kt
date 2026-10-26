package no.nav.syfo.application

import no.nav.syfo.domain.JournalpostId

interface IDokdistClient {
    suspend fun distribuer(journalpostId: JournalpostId): String
}
