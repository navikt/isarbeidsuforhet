package no.nav.syfo.generator

import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.DocumentComponent

fun generateVarsel(
    document: List<DocumentComponent> = generateDocumentComponent(
        fritekst = "En fritekst",
    )
) = Varsel.create(document)
