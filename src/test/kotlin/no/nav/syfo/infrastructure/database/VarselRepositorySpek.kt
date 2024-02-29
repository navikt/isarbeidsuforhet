package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.Varsel
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.database.repository.VarselRepository
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime

class VarselRepositorySpek : Spek({
    describe(VarselRepositorySpek::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database
        val vurderingRepository = VurderingRepository(database = database)
        val varselRepository = VarselRepository(database = database)

        describe("getUnpublishedExpiredVarsler") {

            afterEachTest {
                database.dropData()
            }

            val expiredVarselOneWeekAgo =
                Varsel(
                    generateDocumentComponent("En begrunnelse"),
                    svarfrist = OffsetDateTime.now().minusWeeks(1)
                )
            val expiredVarselYesterday =
                Varsel(
                    generateDocumentComponent("En begrunnelse"),
                    svarfrist = OffsetDateTime.now().minusDays(1)
                )
            val expiredVarselToday =
                Varsel(
                    generateDocumentComponent("En begrunnelse"),
                    svarfrist = OffsetDateTime.now()
                )
            val expiredVarselTomorrow =
                Varsel(
                    generateDocumentComponent("En begrunnelse"),
                    svarfrist = OffsetDateTime.now().plusDays(1)
                )
            val expiredVarselInOneWeek =
                Varsel(
                    generateDocumentComponent("En begrunnelse"),
                    svarfrist = OffsetDateTime.now().plusWeeks(1)
                )

            it("retrieves expired varsler") {
                val vurderinger = listOf(
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselOneWeekAgo),
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselYesterday),
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselToday),
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselTomorrow),
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselInOneWeek),
                )
                vurderinger.forEach {
                    vurderingRepository.createForhandsvarsel(
                        pdf = UserConstants.PDF_FORHANDSVARSEL,
                        vurdering = it,
                    )
                }

                val retrievedExpiredVarsler = varselRepository.getUnpublishedExpiredVarsler()
                retrievedExpiredVarsler.size shouldBeEqualTo 3
            }

            it("retrieves expired varsel that is not published") {
                val expiredVarselNotPublished = expiredVarselOneWeekAgo
                val expiredVarselPublished = expiredVarselYesterday.publishExpiredVarsel()
                val vurderinger = listOf(
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselNotPublished),
                    generateForhandsvarselVurdering().copy(varsel = expiredVarselPublished),
                )
                vurderinger.forEach {
                    vurderingRepository.createForhandsvarsel(
                        pdf = UserConstants.PDF_FORHANDSVARSEL,
                        vurdering = it,
                    )
                }

                val retrievedUnpublishedExpiredVarsler = varselRepository.getUnpublishedExpiredVarsler()
                retrievedUnpublishedExpiredVarsler.size shouldBeEqualTo 1
                retrievedUnpublishedExpiredVarsler.first().second.uuid shouldBeEqualTo expiredVarselNotPublished.uuid
            }
        }
    }
})
