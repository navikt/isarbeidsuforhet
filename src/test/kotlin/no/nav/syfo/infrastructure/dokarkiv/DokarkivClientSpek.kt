package no.nav.syfo.infrastructure.dokarkiv

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.EXISTING_EKSTERN_REFERANSE_UUID
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.generator.generateJournalpostRequest
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.mock.dokarkivConflictResponse
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class DokarkivClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val dokarkivClient = externalMockEnvironment.dokarkivClient

    describe(DokarkivClient::class.java.simpleName) {

        it("journalfører forhåndsvarsel") {
            val journalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om avslag på sykepenger",
                brevkodeType = BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL,
                pdf = PDF_FORHANDSVARSEL,
                vurderingUuid = UUID.randomUUID(),
            )

            runBlocking {
                val response = dokarkivClient.journalfor(
                    journalpostRequest = journalpostRequestForhandsvarsel,
                )

                response.journalpostId shouldBeEqualTo 1
            }
        }

        it("handles conflict from api when eksternRefeanseId exists, and uses the existing journalpostId") {
            val journalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om avslag på sykepenger",
                brevkodeType = BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL,
                pdf = PDF_FORHANDSVARSEL,
                vurderingUuid = EXISTING_EKSTERN_REFERANSE_UUID,
            )

            runBlocking {
                val journalpostResponse =
                    dokarkivClient.journalfor(journalpostRequest = journalpostRequestForhandsvarsel)

                journalpostResponse.journalpostId shouldBeEqualTo dokarkivConflictResponse.journalpostId
                journalpostResponse.journalstatus shouldBeEqualTo dokarkivConflictResponse.journalstatus
            }
        }
    }
})
