package no.nav.syfo.infrastructure.cronjob

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_2_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateForhandsvarselRevarslingDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.VurderingProducer
import no.nav.syfo.infrastructure.kafka.VurderingRecord
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Future

class RepublishForhandsvarselWithAdditionalInfoCronjobSpek : Spek({
    describe(RepublishForhandsvarselWithAdditionalInfoCronjob::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val vurderingRepository = VurderingRepository(database = database)
        val vurderingPdfService = VurderingPdfService(
            externalMockEnvironment.pdfgenClient,
            externalMockEnvironment.pdlClient,
        )
        val journalforingService = JournalforingService(
            dokarkivClient = externalMockEnvironment.dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
        )

        val mockVurderingProducer = mockk<KafkaProducer<String, VurderingRecord>>(relaxed = true)
        val vurderingProducer = VurderingProducer(
            producer = mockVurderingProducer,
        )

        val vurderingService = VurderingService(
            vurderingRepository = vurderingRepository,
            vurderingPdfService = vurderingPdfService,
            journalforingService = journalforingService,
            vurderingProducer = vurderingProducer,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockVurderingProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        afterEachTest {
            database.dropData()
        }

        describe("RepublishForhandsvarselWithAdditionalInfoCronjob") {
            val begrunnelse1 = "En begrunnelse1"
            val svarfrist1 = LocalDate.of(2025, 3, 1)
            val vurderingForhandsvarsel1 = generateForhandsvarselVurdering(
                begrunnelse = begrunnelse1,
                document = generateForhandsvarselRevarslingDocumentComponent(
                    beskrivelse = begrunnelse1,
                    svarfrist = svarfrist1,
                ),
                svarfrist = svarfrist1

            )
            val begrunnelse2 = "En begrunnelse2"
            val svarfrist2 = LocalDate.of(2025, 3, 7)
            val vurderingForhandsvarsel2 = generateForhandsvarselVurdering(
                personident = ARBEIDSTAKER_2_PERSONIDENT,
                begrunnelse = begrunnelse2,
                document = generateForhandsvarselRevarslingDocumentComponent(
                    beskrivelse = begrunnelse2,
                    svarfrist = svarfrist2,
                ),
                svarfrist = svarfrist2
            )

            it("Henter gamle, overskriver teksten og svarfrist") {
                val uuids = listOf(
                    vurderingForhandsvarsel1.uuid.toString(),
                    vurderingForhandsvarsel2.uuid.toString(),
                )
                val republishForhandsvarselWithAdditionalInfoCronjob = RepublishForhandsvarselWithAdditionalInfoCronjob(
                    vurderingService = vurderingService,
                    uuids = uuids,
                )
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel1,
                )
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel2,
                )

                runBlocking {
                    val result = republishForhandsvarselWithAdditionalInfoCronjob.run()
                    result.size shouldBeEqualTo 2
                    result.forEach { it.isSuccess shouldBeEqualTo true }

                    result[0].getOrThrow().begrunnelse shouldBeEqualTo begrunnelse1
                    result[1].getOrThrow().begrunnelse shouldBeEqualTo begrunnelse2

                    val vurderinger1 = vurderingRepository.getVurderinger(personident = vurderingForhandsvarsel1.personident)
                    vurderinger1.size shouldBeEqualTo 2 // Har både nytt og gammelt varsel i databasen
                    vurderinger1.all { it.type == VurderingType.FORHANDSVARSEL }.shouldBeTrue()
                    vurderinger1.first().createdAt shouldBeGreaterThan vurderinger1.last().createdAt // Sortert DESC på created_at, så første er den nyeste
                    vurderinger1.first().varsel?.svarfrist shouldBeEqualTo LocalDate.of(2025, 4, 9)
                    vurderinger1.first().document.filter { documentComponent ->
                        documentComponent.texts.contains(
                            "Viktig informasjon: På grunn av en teknisk feil, har vi ikke klart å varsle deg om dette brevet tidligere. Vi beklager ulempen. På grunn av denne feilen mottar du derfor et nytt brev, med ny frist for tilbakemelding. Dette brevet erstatter tidligere brev som du ikke ble varslet om, og det er kun dette brevet du skal forholde deg til. Det opprinnelige brevet kan du finne under Mine dokumenter på innloggede sider på nav.no.\n"
                        )
                    }.size shouldBeEqualTo 1
                    vurderinger1.first().document.filter { documentComponent ->
                        documentComponent.texts.contains(
                            svarfrist1.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        )
                    }.size shouldBeEqualTo 0 // Inneholder ingen spor av gammel svarfrist
                    vurderinger1.first().journalpostId shouldBeEqualTo null
                    vurderinger1.first().publishedAt shouldBeEqualTo null
                    vurderinger1.first().varsel?.publishedAt shouldBeEqualTo null

                    val vurderinger2 = vurderingRepository.getVurderinger(personident = vurderingForhandsvarsel2.personident)
                    vurderinger2.size shouldBeEqualTo 2 // Har både nytt og gammelt varsel i databasen
                    vurderinger2.all { it.type == VurderingType.FORHANDSVARSEL }.shouldBeTrue()
                    vurderinger2.first().createdAt shouldBeGreaterThan vurderinger2.last().createdAt // Sortert DESC på created_at, så første er den nyeste
                    vurderinger2.first().varsel?.svarfrist shouldBeEqualTo LocalDate.of(2025, 4, 9)
                    vurderinger2.first().document.filter { documentComponent ->
                        documentComponent.texts.contains(
                            "Viktig informasjon: På grunn av en teknisk feil, har vi ikke klart å varsle deg om dette brevet tidligere. Vi beklager ulempen. På grunn av denne feilen mottar du derfor et nytt brev, med ny frist for tilbakemelding. Dette brevet erstatter tidligere brev som du ikke ble varslet om, og det er kun dette brevet du skal forholde deg til. Det opprinnelige brevet kan du finne under Mine dokumenter på innloggede sider på nav.no.\n"                        )
                    }.size shouldBeEqualTo 1
                    vurderinger2.first().document.filter { documentComponent ->
                        documentComponent.texts.contains(
                            svarfrist1.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        )
                    }.size shouldBeEqualTo 0 // Inneholder ingen spor av gammel svarfrist
                    vurderinger2.first().journalpostId shouldBeEqualTo null
                    vurderinger2.first().publishedAt shouldBeEqualTo null
                    vurderinger2.first().varsel?.publishedAt shouldBeEqualTo null
                }
            }
        }
    }
})
