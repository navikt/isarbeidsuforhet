package no.nav.syfo.infrastructure.clients.pdfgen

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.sanitizeForPdfGen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class VurderingPdfDTO private constructor(
    val mottakerNavn: String,
    val mottakerFodselsnummer: String,
    val datoSendt: String,
    val documentComponents: List<DocumentComponent>,
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no", "NO"))

        fun create(
            mottakerNavn: String,
            mottakerPersonident: PersonIdent,
            documentComponents: List<DocumentComponent>,
        ): VurderingPdfDTO =
            VurderingPdfDTO(
                mottakerNavn = mottakerNavn,
                mottakerFodselsnummer = mottakerPersonident.value,
                datoSendt = LocalDate.now().format(formatter),
                documentComponents = documentComponents.sanitizeForPdfGen()
            )
    }
}
