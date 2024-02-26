package no.nav.syfo.application

import no.nav.syfo.domain.model.UnpublishedVarsel

interface IVarselRepository {
    fun getUnpublishedVarsler(): List<UnpublishedVarsel>
    fun setPublished(varsel: UnpublishedVarsel)
}
