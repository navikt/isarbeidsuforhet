package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.domain.*

fun generateForhandsvarselVurdering(
    personident: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    begrunnelse: String = "En begrunnelse",
    document: List<DocumentComponent> = generateDocumentComponent(begrunnelse),
) = Vurdering.createForhandsvarsel(
    personident = personident,
    veilederident = UserConstants.VEILEDER_IDENT,
    begrunnelse = begrunnelse,
    document = document,
)
