package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.domain.PersonIdent
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord

// Basert på https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/avro/no/nav/person/pdl/aktor/AktorV2.avdl

data class KafkaIdenthendelseDTO(
    val identifikatorer: List<Identifikator>,
)

data class Identifikator(
    val idnummer: String,
    val type: IdentType,
    val gjeldende: Boolean,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}

fun KafkaIdenthendelseDTO.getFolkeregisterIdenter(): Pair<PersonIdent?, List<PersonIdent>> {
    val (aktive, inaktive) = identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }.partition { it.gjeldende }
    val aktivIdent = aktive.firstOrNull()?.let { PersonIdent(it.idnummer) }

    return Pair(aktivIdent, inaktive.map { PersonIdent(it.idnummer) })
}

fun GenericRecord.toKafkaIdenthendelseDTO(): KafkaIdenthendelseDTO {
    val identifikatorer = (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
        Identifikator(
            idnummer = it.get("idnummer").toString(),
            gjeldende = it.get("gjeldende").toString().toBoolean(),
            type = when (it.get("type").toString()) {
                "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                "AKTORID" -> IdentType.AKTORID
                "NPID" -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            }
        )
    }
    return KafkaIdenthendelseDTO(identifikatorer)
}
