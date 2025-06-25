package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.VurderingType
import java.time.LocalDate

fun generateForhandsvarselVurdering(
    personident: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    begrunnelse: String = "En begrunnelse",
    document: List<DocumentComponent> = generateDocumentComponent(begrunnelse),
    svarfrist: LocalDate = LocalDate.now().plusDays(30),
) = generateVurdering(
    personident = personident,
    begrunnelse = begrunnelse,
    document = document,
    type = VurderingType.FORHANDSVARSEL,
    svarfrist = svarfrist,
) as Vurdering.Forhandsvarsel

fun generateVurdering(
    personident: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    begrunnelse: String = "En begrunnelse",
    document: List<DocumentComponent> = generateDocumentComponent(begrunnelse),
    type: VurderingType,
    svarfrist: LocalDate? = null,
) = when (type) {
    VurderingType.FORHANDSVARSEL -> Vurdering.Forhandsvarsel(
        personident = personident,
        veilederident = UserConstants.VEILEDER_IDENT,
        begrunnelse = begrunnelse,
        document = document,
        svarfrist = svarfrist!!,
    )

    VurderingType.IKKE_AKTUELL -> Vurdering.IkkeAktuell(
        personident = personident,
        veilederident = UserConstants.VEILEDER_IDENT,
        arsak = Vurdering.IkkeAktuell.Arsak.FRISKMELDT,
        document = document,
    )

    VurderingType.OPPFYLT -> Vurdering.Oppfylt(
        personident = personident,
        veilederident = UserConstants.VEILEDER_IDENT,
        begrunnelse = begrunnelse,
        document = document,
    )

    VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL -> Vurdering.OppfyltUtenForhandsvarsel(
        personident = personident,
        veilederident = UserConstants.VEILEDER_IDENT,
        begrunnelse = begrunnelse,
        document = document,
    )

    VurderingType.AVSLAG -> Vurdering.Avslag(
        personident = personident,
        veilederident = UserConstants.VEILEDER_IDENT,
        begrunnelse = begrunnelse,
        document = document,
        gjelderFom = LocalDate.now(),
    )

    VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> Vurdering.AvslagUtenForhandsvarsel(
        personident = personident,
        veilederident = UserConstants.VEILEDER_IDENT,
        begrunnelse = begrunnelse,
        document = document,
        gjelderFom = LocalDate.now(),
        vurderingInitiertAv = Vurdering.AvslagUtenForhandsvarsel.VurderingInitiertAv.NAV_KONTOR,
        oppgaveFraNayDato = LocalDate.now().minusDays(1),
    )
}
