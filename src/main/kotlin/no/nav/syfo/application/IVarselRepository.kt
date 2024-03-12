package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel

interface IVarselRepository {
    fun getUnpublishedVarsler(): List<Triple<PersonIdent, String, Varsel>>
    fun getUnpublishedExpiredVarsler(): List<Pair<PersonIdent, Varsel>>
    fun update(varsel: Varsel)
}
