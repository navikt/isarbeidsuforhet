package no.nav.syfo.generator

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.DocumentComponentType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

fun generateForhandsvarselRevarslingDocumentComponent(beskrivelse: String, svarfrist: LocalDate) = listOf(
    DocumentComponent(
        type = DocumentComponentType.HEADER_H1,
        title = null,
        texts = listOf("Nav vurderer å avslå sykepengene dine"),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf(
            "OBS! På grunn av en teknisk feil på vår side, har vi ikke klart å varsle deg om dette brevet tidligere. Vi beklager ulempen. Vi har derfor forlenget fristen for å svare til ${svarfrist.format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy")
            )}. Dette brevet er en eksakt kopi av det du skulle ha mottatt tidligere, men med ny utvidet frist.",
            "Nav vurderer å avslå sykepengene dine fra og med ${svarfrist.format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy")
            )}."
        ),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf("For å få sykepenger må du være helt eller delvis ute av stand til å arbeide på grunn av sykdom eller skade."),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf(beskrivelse),
    ),
    DocumentComponent(
        type = DocumentComponentType.HEADER_H3,
        title = null,
        texts = listOf("Du kan uttale deg"),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf(
            "Vi sender deg dette brevet for at du skal ha mulighet til å uttale deg før vi avgjør saken din. Du må sende inn opplysninger eller kontakte oss innen ${svarfrist.format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy")
            )}."
        ),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf("Etter denne datoen vil Nav vurdere å avslå sykepengene dine."),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf(
            "Dersom du blir friskmeldt før ${svarfrist.format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy")
            )} kan du se bort fra dette brevet."
        ),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf("Kontakt oss gjerne på nav.no/skriv-til-oss eller telefon 55 55 33 33."),
    ),
    DocumentComponent(
        type = DocumentComponentType.HEADER_H3,
        title = null,
        texts = listOf("Lovhjemmel"),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf("Krav om arbeidsuførhet er beskrevet i folketrygdloven § 8-4 første ledd."),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf("«Sykepenger ytes til den som er arbeidsufør på grunn av en funksjonsnedsettelse som klart skyldes sykdom eller skade. Arbeidsuførhet som skyldes sosiale eller økonomiske problemer o.l., gir ikke rett til sykepenger.»"),
    ),
    DocumentComponent(
        type = DocumentComponentType.PARAGRAPH,
        key = "Standardtekst",
        title = null,
        texts = listOf("Med vennlig hilsen", "VEILEDER_NAVN", "Nav"),
    ),
)
