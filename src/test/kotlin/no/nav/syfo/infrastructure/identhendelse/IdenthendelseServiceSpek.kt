package no.nav.syfo.infrastructure.identhendelse

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateIdenthendelse
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseService
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

private val aktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
private val inaktivIdent = UserConstants.ARBEIDSTAKER_3_PERSONIDENT
private val annenInaktivIdent = UserConstants.ARBEIDSTAKER_2_PERSONIDENT

class IdenthendelseServiceSpek : Spek({
    describe(IdenthendelseService::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val vurderingRepository = VurderingRepository(database = database)
        val identhendelseService = IdenthendelseService(
            vurderingRepository = vurderingRepository,
        )

        beforeEachTest {
            database.dropData()
        }

        val vurderingMedInaktivIdent = generateVurdering(personident = inaktivIdent, type = VurderingType.OPPFYLT)
        val vurderingMedAnnenInaktivIdent = generateVurdering(personident = annenInaktivIdent, type = VurderingType.AVSLAG)

        it("Flytter vurdering fra inaktiv ident til ny ident når person får ny ident") {
            vurderingRepository.createVurdering(
                vurdering = vurderingMedInaktivIdent,
                pdf = UserConstants.PDF_VURDERING
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = listOf(inaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vurderingRepository.getVurderinger(personident = inaktivIdent).shouldBeEmpty()
            vurderingRepository.getVurderinger(personident = aktivIdent).shouldNotBeEmpty()
        }

        it("Flytter vurderinger fra inaktive identer når person for ny ident") {
            vurderingRepository.createVurdering(
                vurdering = vurderingMedInaktivIdent,
                pdf = UserConstants.PDF_VURDERING
            )
            vurderingRepository.createVurdering(
                vurdering = vurderingMedAnnenInaktivIdent,
                pdf = UserConstants.PDF_VURDERING
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = listOf(inaktivIdent, annenInaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vurderingRepository.getVurderinger(personident = inaktivIdent).shouldBeEmpty()
            vurderingRepository.getVurderinger(personident = annenInaktivIdent).shouldBeEmpty()
            vurderingRepository.getVurderinger(personident = aktivIdent).size shouldBeEqualTo 2
        }

        it("Oppdaterer ingenting når person får ny ident og uten vurdering på inaktiv ident") {
            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = listOf(inaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vurderingRepository.getVurderinger(personident = inaktivIdent).shouldBeEmpty()
            vurderingRepository.getVurderinger(personident = aktivIdent).shouldBeEmpty()
        }

        it("Oppdaterer ingenting når person får ny ident uten inaktiv identer") {
            vurderingRepository.createVurdering(
                vurdering = vurderingMedInaktivIdent,
                pdf = UserConstants.PDF_VURDERING
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = emptyList()
            )
            identhendelseService.handle(identhendelse)

            vurderingRepository.getVurderinger(personident = inaktivIdent).shouldNotBeEmpty()
            vurderingRepository.getVurderinger(personident = aktivIdent).shouldBeEmpty()
        }

        it("Oppdaterer ingenting når person mangler aktiv ident") {
            vurderingRepository.createVurdering(
                vurdering = vurderingMedInaktivIdent,
                pdf = UserConstants.PDF_VURDERING
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = null,
                inaktiveIdenter = listOf(inaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vurderingRepository.getVurderinger(personident = inaktivIdent).shouldNotBeEmpty()
            vurderingRepository.getVurderinger(personident = aktivIdent).shouldBeEmpty()
        }
    }
})
