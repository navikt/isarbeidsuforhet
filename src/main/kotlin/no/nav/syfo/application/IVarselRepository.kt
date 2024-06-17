package no.nav.syfo.application

import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IVarselRepository {
    fun getUnpublishedVarsler(): List<Triple<PersonIdent, JournalpostId, Varsel>>
    fun update(varsel: Varsel)
}
