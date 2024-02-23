package no.nav.syfo.generator

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.DocumentComponentType

fun generateDocumentComponent(fritekst: String, header: String = "Standard header") = listOf(
    DocumentComponent(
        type = DocumentComponentType.HEADER_H1,
        title = null,
        texts = listOf(header),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf(fritekst),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        key = "Standardtekst",
        title = null,
        texts = listOf("Dette er en standardtekst"),
    ),
)
