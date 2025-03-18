package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.DocumentComponentType
import no.nav.syfo.domain.Vurdering
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class RepublishForhandsvarselWithAdditionalInfoCronjob(
    private val vurderingService: VurderingService,
    private val uuids: List<String> = emptyList(),
) : Cronjob {
    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 10000000

    private val log = LoggerFactory.getLogger(RepublishForhandsvarselWithAdditionalInfoCronjob::class.java)

    override suspend fun run(): List<Result<Vurdering>> {
        val newFrist = LocalDate.of(2025, 4, 9) // 7. april 2025
        val result = uuids.map { uuidString ->
            try {
                val uuid = UUID.fromString(uuidString)
                val vurdering = vurderingService.getVurdering(uuid)
                if (vurdering != null) {
                    val vurderingerForPerson = vurderingService.getVurderinger(vurdering.personident)
                    if (vurderingerForPerson.firstOrNull()?.uuid == uuid) {
                        val newDocument = generateNewDocument(vurdering, newFrist)
                        val newVurdering = vurderingService.createVurdering(
                            personident = vurdering.personident,
                            veilederident = vurdering.veilederident,
                            type = vurdering.type,
                            arsak = vurdering.arsak,
                            begrunnelse = vurdering.begrunnelse,
                            document = newDocument,
                            gjelderFom = vurdering.gjelderFom,
                            svarfrist = newFrist,
                            callId = "cronjob-republish-forhandsvarsel",
                            overrideForhandsvarselChecks = true,
                        )
                        Result.success(newVurdering)
                    } else {
                        Result.failure(IllegalArgumentException("Vurdering with UUID $uuid already revarslet"))
                    }
                } else {
                    Result.failure(IllegalArgumentException("Vurdering with UUID $uuid not found"))
                }
            } catch (e: Exception) {
                log.error("Exception caught while attempting to republish forhandsvarsel with UUID $uuidString", e)
                Result.failure(e)
            }
        }
        log.info(
            """
            Updated ${result.count { it.isSuccess }} forhandsvarsler in ${RepublishForhandsvarselWithAdditionalInfoCronjob::class.java.simpleName}.
            UUIDs for new forhandsvarsler: ${result.filter { it.isSuccess }.joinToString(", ") { it.getOrNull()?.uuid.toString() }}
            """.trimIndent()
        )

        return result
    }

    private fun generateNewDocument(vurdering: Vurdering, newFrist: LocalDate): List<DocumentComponent> {
        return vurdering.document.map { documentComponent ->
            if (documentComponent.type == DocumentComponentType.PARAGRAPH &&
                documentComponent.texts.any { it.contains("Nav vurderer å avslå sykepengene dine fra og med") }
            ) {
                val extraText = "Viktig informasjon: På grunn av en teknisk feil, har vi ikke klart å varsle deg om dette brevet tidligere. Vi beklager ulempen. På grunn av denne feilen mottar du derfor et nytt brev, med ny frist for tilbakemelding. Dette brevet erstatter tidligere brev som du ikke ble varslet om, og det er kun dette brevet du skal forholde deg til. Det opprinnelige brevet kan du finne under Mine dokumenter på innloggede sider på nav.no.\n"
                DocumentComponent(
                    type = DocumentComponentType.PARAGRAPH,
                    title = null,
                    texts = listOf(
                        extraText,
                        "Nav vurderer å avslå sykepengene dine fra og med ${newFrist.format(
                            DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        )}."
                    )
                )
            } else if (documentComponent.type == DocumentComponentType.PARAGRAPH &&
                documentComponent.texts.any { it.contains("Vi sender deg dette brevet for at du skal ha mulighet til å uttale deg før vi avgjør saken din. Du må sende inn opplysninger eller kontakte oss innen") }
            ) {
                DocumentComponent(
                    type = DocumentComponentType.PARAGRAPH,
                    title = null,
                    texts = listOf(
                        "Vi sender deg dette brevet for at du skal ha mulighet til å uttale deg før vi avgjør saken din. Du må sende inn opplysninger eller kontakte oss innen ${newFrist.format(
                            DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        )}."
                    )
                )
            } else if (documentComponent.type == DocumentComponentType.PARAGRAPH &&
                documentComponent.texts.any { it.contains("Dersom du blir friskmeldt før") }
            ) {
                DocumentComponent(
                    type = DocumentComponentType.PARAGRAPH,
                    title = null,
                    texts = listOf(
                        "Dersom du blir friskmeldt før ${newFrist.format(
                            DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        )} kan du se bort fra dette brevet."
                    )
                )
            } else {
                documentComponent
            }
        }
    }
}
