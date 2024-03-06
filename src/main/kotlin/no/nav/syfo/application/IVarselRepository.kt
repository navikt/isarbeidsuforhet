package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import java.util.UUID

interface IVarselRepository {
    fun getUnpublishedVarsler(): List<Pair<PersonIdent, Varsel>>
    fun getUnpublishedExpiredVarsler(): List<Pair<PersonIdent, Varsel>>
    fun update(varsel: Varsel)
    fun getVurdering(varsel: Varsel): Vurdering?
}
