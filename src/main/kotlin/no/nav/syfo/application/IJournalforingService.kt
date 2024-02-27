package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import java.util.*

interface IJournalforingService {
    suspend fun journalfor(
        personident: PersonIdent,
        pdf: ByteArray,
        varselUUID: UUID,
    ): Int
}
