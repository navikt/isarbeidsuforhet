package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(private val vurderingRepository: VurderingRepository) {

    fun handle(identhendelse: KafkaIdenthendelseDTO) {
        val (aktivIdent, inaktiveIdenter) = identhendelse.getFolkeregisterIdenter()
        if (aktivIdent != null) {
            val vurderingerMedInaktivIdent = inaktiveIdenter.flatMap { vurderingRepository.getVurderinger(it) }

            if (vurderingerMedInaktivIdent.isNotEmpty()) {
                vurderingRepository.updatePersonident(
                    nyPersonident = aktivIdent,
                    vurderinger = vurderingerMedInaktivIdent,
                )
                log.info("Identhendelse: Updated ${vurderingerMedInaktivIdent.size} vurderinger based on Identhendelse from PDL")
            }
        } else {
            log.warn("Identhendelse ignored - Mangler aktiv ident i PDL")
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
