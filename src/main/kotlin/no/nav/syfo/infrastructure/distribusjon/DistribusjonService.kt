package no.nav.syfo.infrastructure.distribusjon

import no.nav.syfo.application.IDokdistClient
import no.nav.syfo.common.distribusjon.client.DokdistfordelingClient
import no.nav.syfo.common.distribusjon.createDistribuerJournalpostRequest
import no.nav.syfo.common.distribusjon.dto.Distribusjonstidspunkt
import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.domain.JournalpostId

class DistribusjonService(
    private val dokdistfordelingClient: DokdistfordelingClient,
) : IDokdistClient {
    override suspend fun distribuer(journalpostId: JournalpostId): String {
        val request = createDistribuerJournalpostRequest(
            journalpostId = journalpostId.value,
            bestillendeFagsystem = "MODIA_SYFO",
            dokumentProdApp = "isarbeidsuforhet",
            distribusjonstype = Distribusjonstype.VIKTIG,
            distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID,
        )
        return dokdistfordelingClient.distribuer(request).bestillingsId
    }
}
