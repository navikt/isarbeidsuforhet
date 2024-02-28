package no.nav.syfo.infrastructure.kafka.esyfovarsel.dto

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed interface EsyfovarselHendelse : Serializable {
    val type: HendelseType
    var data: Any?
}

data class ArbeidstakerHendelse(
    override val type: HendelseType,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?
) : EsyfovarselHendelse

data class VarselData(
    val journalpost: VarselDataJournalpost? = null,
)

data class VarselDataJournalpost(
    val uuid: String,
    val id: String?
)

enum class HendelseType {
    SM_ARBEIDSUFORHET_FORHANDSVARSEL,
}
