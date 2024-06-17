package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerForhandsvarselProducer

class VarselProducer(
    private val arbeidstakerForhandsvarselProducer: ArbeidstakerForhandsvarselProducer,
) : IVarselProducer {

    override fun sendArbeidstakerForhandsvarsel(
        personIdent: PersonIdent,
        journalpostId: JournalpostId,
        varsel: Varsel
    ): Result<Varsel> {
        return arbeidstakerForhandsvarselProducer.sendArbeidstakerForhandsvarsel(personIdent = personIdent, journalpostId = journalpostId, varsel = varsel)
    }
}
