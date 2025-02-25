package no.nav.syfo.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.kafka.identhendelse.IdentType
import no.nav.syfo.infrastructure.kafka.identhendelse.Identifikator
import no.nav.syfo.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO

fun generateIdenthendelse(
    aktivIdent: PersonIdent?,
    inaktiveIdenter: List<PersonIdent>
): KafkaIdenthendelseDTO {
    val identifikatorer = inaktiveIdenter.map { Identifikator(idnummer = it.value, gjeldende = false, type = IdentType.FOLKEREGISTERIDENT) }.toMutableList()
    aktivIdent?.let { identifikatorer.add(Identifikator(idnummer = it.value, gjeldende = true, type = IdentType.FOLKEREGISTERIDENT)) }

    return KafkaIdenthendelseDTO(
        identifikatorer = identifikatorer
    )
}
