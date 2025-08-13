package no.nav.syfo.domain

import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostType
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

sealed class Vurdering(
    open val uuid: UUID,
    open val createdAt: OffsetDateTime,
    open val personident: PersonIdent,
    open val veilederident: String,
    open val type: VurderingType,
    open val begrunnelse: String,
    open val document: List<DocumentComponent>,
    open val journalpostId: JournalpostId?,
    open val publishedAt: OffsetDateTime?,
) {
    abstract val journalpostType: JournalpostType

    fun journalfor(journalpostId: JournalpostId): Vurdering = when (this) {
        is Forhandsvarsel -> this.copy(journalpostId = journalpostId)
        is Oppfylt -> this.copy(journalpostId = journalpostId)
        is OppfyltUtenForhandsvarsel -> this.copy(journalpostId = journalpostId)
        is Avslag -> this.copy(journalpostId = journalpostId)
        is AvslagUtenForhandsvarsel -> this.copy(journalpostId = journalpostId)
        is IkkeAktuell -> this.copy(journalpostId = journalpostId)
    }

    fun publish(): Vurdering = when (this) {
        is Forhandsvarsel -> this.copy(publishedAt = nowUTC())
        is Oppfylt -> this.copy(publishedAt = nowUTC())
        is OppfyltUtenForhandsvarsel -> this.copy(publishedAt = nowUTC())
        is Avslag -> this.copy(publishedAt = nowUTC())
        is AvslagUtenForhandsvarsel -> this.copy(publishedAt = nowUTC())
        is IkkeAktuell -> this.copy(publishedAt = nowUTC())
    }

    fun isExpiredForhandsvarsel(): Boolean = this is Forhandsvarsel && this.varsel.isExpired()

    fun gjelderFom(): LocalDate? =
        when (this) {
            is Avslag -> gjelderFom
            is AvslagUtenForhandsvarsel -> gjelderFom
            else -> null
        }

    fun oppgaveFraNayDato(): LocalDate? =
        when (this) {
            is OppfyltUtenForhandsvarsel -> oppgaveFraNayDato
            is AvslagUtenForhandsvarsel -> oppgaveFraNayDato
            else -> null
        }

    data class Forhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        val varsel: Varsel,
    ) : Vurdering(
        uuid,
        createdAt,
        personident,
        veilederident,
        VurderingType.FORHANDSVARSEL,
        begrunnelse,
        document,
        journalpostId,
        publishedAt,
    ) {
        override val journalpostType: JournalpostType = JournalpostType.UTGAAENDE

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            svarfrist: LocalDate,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
            varsel = Varsel(svarfrist = svarfrist),
        )
    }

    data class Oppfylt internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null
    ) : Vurdering(
        uuid,
        createdAt,
        personident,
        veilederident,
        VurderingType.OPPFYLT,
        begrunnelse,
        document,
        journalpostId,
        publishedAt,
    ) {
        override val journalpostType: JournalpostType = JournalpostType.UTGAAENDE

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
        )
    }

    data class OppfyltUtenForhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        val oppgaveFraNayDato: LocalDate? = null,
    ) : Vurdering(
        uuid,
        createdAt,
        personident,
        veilederident,
        VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL,
        begrunnelse,
        document,
        journalpostId,
        publishedAt,
    ) {
        override val journalpostType: JournalpostType = JournalpostType.NOTAT

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            oppgaveFraNayDato: LocalDate?,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
            oppgaveFraNayDato = oppgaveFraNayDato,
        )
    }

    data class Avslag internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        val gjelderFom: LocalDate,
    ) : Vurdering(
        uuid,
        createdAt,
        personident,
        veilederident,
        VurderingType.AVSLAG,
        begrunnelse,
        document,
        journalpostId,
        publishedAt,
    ) {
        /**
         * `Vurdering.Avslag` har JournalpostType.NOTAT fordi NAY har vedtaksmyndighet og det er de som skal sende ut selve vedtaket.
         */
        override val journalpostType: JournalpostType = JournalpostType.NOTAT

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            gjelderFom: LocalDate,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            gjelderFom = gjelderFom,
            journalpostId = null,
            publishedAt = null,
        )
    }

    data class AvslagUtenForhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        val gjelderFom: LocalDate,
        val vurderingInitiertAv: VurderingInitiertAv,
        val oppgaveFraNayDato: LocalDate? = null,
    ) : Vurdering(
        uuid,
        createdAt,
        personident,
        veilederident,
        VurderingType.AVSLAG_UTEN_FORHANDSVARSEL,
        begrunnelse,
        document,
        journalpostId,
        publishedAt,
    ) {
        /**
         * `Vurdering.AvslagUtenForhandsvarsel` har JournalpostType.NOTAT fordi NAY har vedtaksmyndighet og det er de som skal sende ut selve vedtaket.
         */
        override val journalpostType: JournalpostType = JournalpostType.NOTAT

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            gjelderFom: LocalDate,
            vurderingInitiertAv: VurderingInitiertAv,
            oppgaveFraNayDato: LocalDate?,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
            gjelderFom = gjelderFom,
            vurderingInitiertAv = vurderingInitiertAv,
            oppgaveFraNayDato = oppgaveFraNayDato,
        )

        enum class VurderingInitiertAv {
            NAV_KONTOR,
            NAY,
        }
    }

    data class IkkeAktuell internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        val arsak: Arsak,
    ) : Vurdering(
        uuid,
        createdAt,
        personident,
        veilederident,
        VurderingType.IKKE_AKTUELL,
        "",
        document,
        journalpostId,
        publishedAt,
    ) {
        override val journalpostType: JournalpostType = JournalpostType.UTGAAENDE

        constructor(
            personident: PersonIdent,
            veilederident: String,
            document: List<DocumentComponent>,
            arsak: Arsak,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            document = document,
            journalpostId = null,
            publishedAt = null,
            arsak = arsak,
        )

        enum class Arsak {
            FRISKMELDT,
            FRISKMELDING_TIL_ARBEIDSFORMIDLING,
        }
    }

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            personident: PersonIdent,
            veilederident: String,
            type: String,
            arsak: String?,
            begrunnelse: String,
            document: List<DocumentComponent>,
            journalpostId: JournalpostId?,
            varsel: Varsel?,
            publishedAt: OffsetDateTime?,
            gjelderFom: LocalDate?,
            vurderingInitiertAv: AvslagUtenForhandsvarsel.VurderingInitiertAv?,
            oppgaveFraNayDato: LocalDate?,
        ): Vurdering {
            return when (VurderingType.valueOf(type)) {
                VurderingType.FORHANDSVARSEL -> Forhandsvarsel(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    varsel = varsel!!,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt,
                )

                VurderingType.OPPFYLT -> Oppfylt(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt,
                )

                VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL -> OppfyltUtenForhandsvarsel(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt,
                    oppgaveFraNayDato = oppgaveFraNayDato,
                )

                VurderingType.AVSLAG -> Avslag(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    gjelderFom = gjelderFom!!,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt,
                )

                VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> AvslagUtenForhandsvarsel(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    gjelderFom = gjelderFom!!,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt,
                    vurderingInitiertAv = vurderingInitiertAv!!,
                    oppgaveFraNayDato = oppgaveFraNayDato,
                )

                VurderingType.IKKE_AKTUELL -> IkkeAktuell(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    arsak = IkkeAktuell.Arsak.valueOf(arsak!!),
                    document = document,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt,
                )
            }
        }
    }
}

enum class VurderingType(val isFinal: Boolean) {
    FORHANDSVARSEL(false),
    OPPFYLT(true),
    OPPFYLT_UTEN_FORHANDSVARSEL(true),
    AVSLAG(true),
    AVSLAG_UTEN_FORHANDSVARSEL(true),
    IKKE_AKTUELL(true);
}

fun VurderingType.getDokumentTittel(): String = when (this) {
    VurderingType.FORHANDSVARSEL -> "Forhåndsvarsel om avslag på sykepenger"
    VurderingType.OPPFYLT, VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL, VurderingType.IKKE_AKTUELL -> "Vurdering av §8-4 arbeidsuførhet"
    VurderingType.AVSLAG, VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> "Innstilling om avslag"
}

fun VurderingType.getBrevkode(): BrevkodeType = when (this) {
    VurderingType.FORHANDSVARSEL -> BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL
    VurderingType.OPPFYLT, VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL, VurderingType.IKKE_AKTUELL -> BrevkodeType.ARBEIDSUFORHET_VURDERING
    VurderingType.AVSLAG, VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> BrevkodeType.ARBEIDSUFORHET_AVSLAG
}
