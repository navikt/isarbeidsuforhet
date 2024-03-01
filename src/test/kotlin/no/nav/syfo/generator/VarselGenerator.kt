package no.nav.syfo.generator

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Varsel

fun generateVarsel(
    document: List<DocumentComponent> = generateDocumentComponent(
        fritekst = "En fritekst",
    )
) = Varsel(document)
