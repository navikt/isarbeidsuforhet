package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerForhandsvarselProducer

class VarselProducer(
    private val arbeidstakerForhandsvarselProducer: ArbeidstakerForhandsvarselProducer,
    private val expiredForhandsvarselProducer: ExpiredForhandsvarselProducer
) : IVarselProducer {

    override fun sendArbeidstakerForhandsvarsel(personIdent: PersonIdent, vurdering: Vurdering): Result<Varsel> {
        return arbeidstakerForhandsvarselProducer.sendArbeidstakerForhandsvarsel(personIdent = personIdent, vurdering = vurdering)
    }

    override fun sendExpiredForhandsvarsel(personIdent: PersonIdent, varsel: Varsel): Result<Varsel> {
        return expiredForhandsvarselProducer.send(personIdent = personIdent, varsel = varsel)
    }
}
